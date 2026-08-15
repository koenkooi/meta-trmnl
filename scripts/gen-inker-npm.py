#!/usr/bin/env python3
"""Generate recipes-trmnl/inker/<prefix>-{prod,dev}.inc plus matching
node_modules layout maps from an npm package-lock.json (lockfileVersion
>= 2/3).

bitbake's npm/npmsw fetchers are both disabled upstream (SkipRecipe,
"disabled due to security issues" -- see bitbake/lib/bb/fetch2/npm*.py),
so this mirrors the terminus/larapaper pattern instead: plain
https://registry.npmjs.org/... SRC_URI entries pinned by a sha256 this
script computes itself (npm's own lockfile "integrity" is sha512, not
directly usable as a bitbake checksum flag pair the project already
uses elsewhere), no custom fetcher.

Splits packages into prod (dev:false in the lockfile) and dev
(dev:true, needed only to run tsc/prisma generate at build time, never
shipped) closures, and filters optional platform-specific packages
(sharp, esbuild, rollup, tailwindcss-oxide, lightningcss, ...) down to
the linux/arm64/glibc variant this project targets -- matching npm's
own os/cpu selection logic, minus the musl duplicates.

Usage: gen-inker-npm.py <package-lock.json> <output-prefix> <out-dir>
"""
import sys
import re
import json
import hashlib
import pathlib
import urllib.request
import concurrent.futures

TARGET_OS = "linux"
TARGET_CPU = "arm64"


def safe_id(name, version):
    # bitbake SRC_URI "name" flags become checksum flag names
    # (SRC_URI[<name>.sha256sum]) and must not start with a dash --
    # scoped package names ("@foo/bar") sanitize to a leading dash
    # otherwise, so always prefix.
    return "npm-" + re.sub(r"[^A-Za-z0-9._+-]", "-", f"{name}-{version}")


def platform_ok(entry):
    os_list = entry.get("os")
    cpu_list = entry.get("cpu")
    if not os_list and not cpu_list:
        return True
    if os_list and TARGET_OS not in os_list:
        return False
    if cpu_list and TARGET_CPU not in cpu_list:
        return False
    return True


def load_packages(lock_path):
    with open(lock_path) as f:
        lock = json.load(f)
    return lock["packages"]


def collect(packages):
    """Returns (fetch: {(name,version): {"resolved","integrity","dev"}},
    path_map: {path: (name,version)})"""
    fetch = {}
    path_map = {}
    for path, entry in packages.items():
        if path == "" or not path.startswith("node_modules/"):
            continue
        if entry.get("link"):
            continue
        if entry.get("inBundle"):
            continue
        name = path.split("node_modules/")[-1]
        version = entry.get("version")
        resolved = entry.get("resolved")
        integrity = entry.get("integrity")
        if not version or not resolved or not integrity:
            continue
        if entry.get("optional") and not platform_ok(entry):
            continue
        if "musl" in name:
            continue
        key = (name, version)
        dev = bool(entry.get("dev", False))
        if key in fetch:
            # a package needed by both prod and dev subgraphs is prod
            fetch[key]["dev"] = fetch[key]["dev"] and dev
        else:
            fetch[key] = {"resolved": resolved, "integrity": integrity, "dev": dev}
        path_map[path] = {"name": name, "version": version}
    return fetch, path_map


def sha256_of(url):
    h = hashlib.sha256()
    with urllib.request.urlopen(url, timeout=60) as r:
        while True:
            chunk = r.read(1 << 16)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def main():
    lock_path, prefix, out_dir = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
    out_dir.mkdir(parents=True, exist_ok=True)

    packages = load_packages(lock_path)
    fetch, path_map = collect(packages)

    print(f"{prefix}: {len(fetch)} unique packages to fetch, "
          f"{len(path_map)} node_modules paths", file=sys.stderr)

    ids = {}
    for key in fetch:
        name, version = key
        ids[key] = safe_id(name, version)

    def work(key):
        name, version = key
        info = fetch[key]
        sha = sha256_of(info["resolved"])
        return key, sha

    hashes = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=24) as ex:
        for i, (key, sha) in enumerate(ex.map(work, fetch.keys())):
            hashes[key] = sha
            if (i + 1) % 100 == 0:
                print(f"  {i + 1}/{len(fetch)}", file=sys.stderr)

    prod_lines = []
    dev_lines = []
    prod_sums = []
    dev_sums = []
    for key in sorted(fetch, key=lambda k: ids[k]):
        name, version = key
        info = fetch[key]
        bid = ids[key]
        fname = f"{bid}.tgz"
        line = f"    {info['resolved']};downloadfilename={fname};unpack=0;name={bid} \\"
        sumline = f'SRC_URI[{bid}.sha256sum] = "{hashes[key]}"'
        if info["dev"]:
            dev_lines.append(line)
            dev_sums.append(sumline)
        else:
            prod_lines.append(line)
            prod_sums.append(sumline)

    def write_inc(name, lines, sums, count):
        path = out_dir / f"{prefix}-{name}.inc"
        with open(path, "w") as f:
            f.write(f"# Auto-generated by scripts/gen-inker-npm.py -- do not hand-edit.\n")
            f.write(f"# {name} npm closure: {count} packages.\n\n")
            if lines:
                # A "SRC_URI += \"\\\n\n    \"" block (backslash-continued
                # with a blank line in the middle, what a naive empty-list
                # join produces) fails bitbake's parser outright -- confirmed
                # live ("unparsed line: 'SRC_URI += \"'"). Skip the whole
                # block when there is nothing to add, rather than emitting
                # a syntactically-broken empty one.
                f.write('SRC_URI += "\\\n')
                f.write("\n".join(lines))
                f.write('\n    "\n\n')
                f.write("\n".join(sums) + "\n")
            else:
                f.write("# (empty closure, nothing to fetch)\n")
        print(f"wrote {path}", file=sys.stderr)

    write_inc("prod", prod_lines, prod_sums, len(prod_lines))
    write_inc("dev", dev_lines, dev_sums, len(dev_lines))

    file_map = {}
    for path, pv in path_map.items():
        key = (pv["name"], pv["version"])
        file_map[path] = {
            "name": pv["name"],
            "version": pv["version"],
            "file": f"{ids[key]}.tgz",
            "dev": fetch[key]["dev"],
        }

    map_path = out_dir / f"{prefix}-map.json"
    with open(map_path, "w") as f:
        json.dump(file_map, f, indent=1, sort_keys=True)
    print(f"wrote {map_path}", file=sys.stderr)


if __name__ == "__main__":
    main()

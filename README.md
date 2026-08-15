# meta-trmnl

An OpenEmbedded layer that builds self-hosted BYOS servers for [TRMNL](https://usetrmnl.com) e-ink displays into bootable images. If you own a TRMNL-compatible device and want it to talk to a server you run instead of the vendor cloud, this layer gives you an image with that server, its database and its supporting services already in it. Three server implementations are packaged -- Terminus, Inker and LaraPaper -- each as an OE recipe plus an image, with the containerised servers deployable either as podman Quadlet units or through a generated compose file. Everything a device needs is in the image: no registry pull, no network fetch at boot, no outbound internet required to pair or serve screens.

## Status

Verified on real `qemuarm64` boots, 2026-08-15:

| Server | Device API | Web UI | Images |
|---|---|---|---|
| Terminus (Ruby/Hanami) | works | works | `terminus-container-image`, `trmnl-image` (baremetal) |
| Inker (NestJS + React) | works | works | `inker-container-image`, `inker-image` (baremetal) |
| LaraPaper (Laravel/PHP) | works | broken | `larapaper-image` (baremetal) |

**Terminus** works end to end through both deploy methods: `/up` returns 302, `/login` renders the real Rodauth login page, and `/api/setup` returns a genuine 200 with an `api_key` and `friendly_id` for a device sending `Model: og`. Postgres, Valkey, web and worker all come up healthy with no restarts.

**Inker** works end to end through both deploy methods: `/api/setup` returns a real 200, and the dashboard renders correctly (a PIN entry screen; Inker's default PIN is `1111`).

**LaraPaper** answers the device API for real -- `/api/setup` and `/api/display` both 200 -- but every HTML route 500s because the Vite asset manifest is not built by any recipe here. It is usable as a device backend, not as a dashboard, and it has no container image.

## What the build needs

`kas/trmnl.yml` composes all of it, standalone -- nothing here needs another repository:

- `openembedded-core` -- the base every recipe here builds on.
- `meta-virtualization` -- `image-oci.bbclass` and podman, used to build and run the containerised servers.
- [`meta-rubygems`](https://github.com/priv-kweihmann/meta-rubygems) (pinned at `dba6dfb`) -- Terminus's native-extension gems use `rubygems.bbclass`.
- `meta-oe` (from meta-openembedded) -- postgresql, valkey, nodejs, php, imagemagick.
- `meta-webserver` (from meta-openembedded) -- nginx, which serves Inker's frontend bundle.
- `meta-clang` plus `meta-browser` (`meta-chromium`) -- `chromium-ozone-wayland`. Terminus renders every device screen through Ferrum, which drives a real headless Chromium binary; without it `/api/display` fails. Budget for it -- this is a full Chromium build, and it is the one piece of this composition not built to completion in this repository's own testing: `bitbake -n terminus-container-image inker-container-image` confirms the real dependency graph resolves cleanly against this file with zero missing providers, but the multi-hour compile itself has not been run end to end here.

## Building

Pick an image target explicitly; the default target is not one of this layer's:

```
kas build kas/trmnl.yml --target terminus-container-image
kas build kas/trmnl.yml --target inker-container-image
```

Baremetal variants, with the server installed straight into the rootfs instead of running as containers: `trmnl-image` (Terminus), `inker-image` (Inker), `larapaper-image` (LaraPaper).

### Quadlet or compose

The two containerised images can deploy their containers two ways, selected at build time by `TRMNL_DEPLOY_METHOD`:

- **`podman`** (the default, `TRMNL_DEPLOY_METHOD` unset or `"podman"`): podman Quadlet -- `.container`/`.volume` units in `/etc/containers/systemd/`, started by systemd at boot.
- **`docker`** (`TRMNL_DEPLOY_METHOD = "docker"`): compose -- a generated `compose.yml` driven by `trmnl-compose@<app>.service`, run through whichever of `podman-compose`/`docker-compose` is on `PATH`. This layer's own recipe hardcodes `RDEPENDS` on `podman podman-compose`, so it is podman-backed on every image this layer builds; the switch exists so a distro that instead installs a real docker engine and `docker-compose` can reuse the same generated compose file without this layer's recipe needing to change.

Append the `kas/trmnl-compose.yml` fragment to opt into compose:

```
kas build kas/trmnl.yml:kas/trmnl-compose.yml --target terminus-container-image
```

Both paths run the same already-imported `localhost/trmnl-<app>:latest` images against the same named volumes on the host network, so exactly one of them may be active for a given app -- running both would collide on ports and write the same volumes from two orchestrators. The generated compose file is written to `/usr/share/trmnl/compose/<app>/compose.yml` either way, so it is readable on the device even under the Quadlet default.

kas requires every colon-concatenated fragment to live in one git root, so any other repository composing this layer (meta-angstrom does) carries its own verbatim copy of `trmnl-compose.yml`; use the one belonging to whichever repository you actually run `kas` from.

## Running the server

These are ordinary OE images, deployed the ordinary way. Artifacts land in `tmp/deploy/images/<machine>/` as `ext4.zst`, `tar.zst` and `tar.gz` -- write the rootfs to your target's storage, or boot the `qemuarm64` build under `runqemu`. Everything above is verified on `qemuarm64`; other machines are not blocked by anything in this layer, just unproven.

For a real device to reach the server, the server needs an address on the device's network. A qemu guest behind user-mode networking is not reachable from a device on your LAN, so pairing a physical display means real hardware or a bridged VM.

On first boot, a `trmnl-oci-import@<instance>.service` imports each OCI archive baked into the rootfs into podman before the containers start. Terminus has four (postgres, valkey, web, worker; web and worker are ~345 MB each), Inker has two much smaller ones. Import logs persist at `/var/lib/containers/trmnl-oci-import/<app>.log`.

Check on it with `podman ps`, `systemctl status terminus-web.service` (Quadlet) or `systemctl status trmnl-compose@terminus.service` (compose); `trmnl-compose.sh <app> up|down` drives the compose path by hand.

Every container runs with `Network=host`, so the server listens on the machine's own ports:

- **Terminus**: `http://<host>:2300`.
- **Inker**: `http://<host>:80`, nginx serving the frontend and proxying `/api`, `/assets` and `/uploads` to the backend on `3002`.

Two settings need your attention before this is more than a test deployment. Terminus's `API_URI` is baked as `http://127.0.0.1:2300`, which is a loopback URL no device can use -- set it to the exact URL your device will be given (see pairing below) by adding an `Environment=API_URI=http://<host>:2300` line to the `[Container]` section of `/etc/containers/systemd/terminus-web.container` and `terminus-worker.container` (then `systemctl daemon-reload`), by editing the compose file's environment under the compose path, or by changing the recipe before you build. Terminus's `APP_SECRET` is a placeholder string in the shipped environment and should be replaced with a real secret.

There is no TLS anywhere in this design, by choice -- the device firmware never verifies certificates (see `docs/pairing.md`), so plain HTTP on a trusted LAN is the supported shape.

Application state lives in podman named volumes (`trmnl-terminus-pgdata`, `trmnl-terminus-uploads`, `trmnl-terminus-fonts`, `trmnl-terminus-keyvalue`, `trmnl-inker-data`) under `/var/lib/containers/storage/volumes` inside the rootfs. An opkg upgrade leaves them alone; reflashing the rootfs destroys them, so back them up (`podman volume export`) before you reflash.

## Pairing your device

Full detail, including the HTTP contract a server has to satisfy, is in [`docs/pairing.md`](docs/pairing.md). The critical path:

1. Hold the device's back button for about five seconds to raise its captive portal.
2. Open **Advanced Configuration -> Custom Server** (a hidden field) and type your server's base URL, e.g. `http://192.168.1.50:2300` for Terminus or `http://192.168.1.50` for Inker.
3. Save and let the device check in. It re-pairs against the new server on its next check-in, fetching a fresh `api_key`/`friendly_id` from `/api/setup`.

No reflash, no DNS override, no compile-time define: this works on any stock device running firmware 1.4.6 or newer. Clearing the field sends the device back to the vendor cloud.

The mistake that costs people an afternoon: **the server's own configured base URL and the URL you typed into the portal must be byte-identical**, because the server embeds that URL in the absolute `image_url` it hands back. A mismatch pairs the device successfully and then leaves the screen blank forever, with no error anywhere. For Terminus that setting is `API_URI`, above.

Terminus maps the device's `Model` header through its own name table before looking the model up in the database, and this layer seeds the `og_plus` row, which is what a device reporting `og` resolves to. A device reporting some other model needs its row seeded before `/api/setup` will succeed.

## Licensing

The servers carry their own upstream licences: Terminus MIT, LaraPaper MIT, Inker AGPL-3.0. This layer's own recipes and metadata are MIT, see [LICENSE](LICENSE). Device firmware is never built or redistributed here -- the servers speak to a device's HTTP API, they do not embed its firmware.

Building an image and running it yourself carries no distribution obligation under any of these. If you hand a built Inker image to someone else, or offer Inker over a network to other people, AGPL-3.0 requires you to make the corresponding source available: the recipe pins Inker's `SRCREV`, so publishing that pin -- plus any patches you carry against Inker's own source -- alongside the image satisfies it.

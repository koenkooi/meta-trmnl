SUMMARY = "Vendored Composer dependency closure for the LaraPaper TRMNL server"
DESCRIPTION = "Production package closure of usetrmnl/larapaper's composer.lock \
(its \"packages\" array, packages-dev excluded -- matches --no-dev). Each \
package zip is fetched directly from its own composer.lock dist URL, \
pinned by a sha256 this layer computed itself (composer.lock's own \
\"shasum\" field is routinely empty; composer verifies via git \"reference\" \
instead). do_compile drives the real composer.phar entirely offline: \
each zip is extracted to its own directory (version injected into its \
composer.json, which GitHub zipballs never carry), then composer.lock's \
own per-package \"dist\" is rewritten to point at that directory \
(type \"path\") -- see do_compile for why a \"repositories\" entry in \
composer.json (artifact- or path-type, both tried first) has zero \
effect here: composer install from an existing lock never consults \
repositories for packages that are already locked, it downloads each \
one using the dist URL recorded IN THE LOCK FILE itself. That's the \
whole point of a lock file -- resolution already happened."
HOMEPAGE = "https://github.com/usetrmnl/larapaper"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS = "php-native unzip-native"

SRC_URI = "git://github.com/usetrmnl/larapaper.git;protocol=https;branch=main \
           https://getcomposer.org/download/2.10.2/composer.phar;downloadfilename=composer.phar;unpack=0 \
           "
SRCREV = "e8e7ae5509a8d26e02c2c953f47555494fa8f4e5"
PV = "0.1+git"

include larapaper-vendor.inc

SRC_URI[sha256sum] = "5ee7125f8a30a34d246cefdc0bc85b8a783b28f2aec968994118512350d28027"

# composer install verifies composer.json's platform requirements
# (php ^8.4, ext-imagick, ext-simplexml, ext-zip) against whatever PHP
# is running composer itself, i.e. php-native here, not the target's
# php -- --ignore-platform-reqs is correct: we already trust the fully
# resolved composer.lock, we're not asking composer to re-resolve
# anything, just to place the pinned versions on disk.
LARAPAPER_COMPOSER_ARGS = "install --no-dev --no-scripts --no-plugins \
    --ignore-platform-reqs --optimize-autoloader --no-interaction --no-progress"

do_compile() {
    rm -rf ${B}/vendor-src ${B}/composer-home ${B}/composer-cache
    mkdir -p ${B}/vendor-src ${B}/composer-home ${B}/composer-cache

    # Extract each zip to its own directory; do_compile below rewrites
    # composer.lock's own per-package "dist" to point straight at
    # these, so no archive-format matching is left to composer at all.
    for zip in ${UNPACKDIR}/composer-vendor-*.zip; do
        base=$(basename "$zip" .zip)
        ver=$(echo "$base" | sed -E 's/^composer-vendor-.+-([Vv]?[0-9][0-9A-Za-z.+_-]*)$/\1/')
        dest="${B}/vendor-src/$base"
        tmp=$(mktemp -d)
        unzip -q "$zip" -d "$tmp"
        top=$(find "$tmp" -mindepth 1 -maxdepth 1 -type d)
        mkdir -p "$dest"
        cp -a "$top"/. "$dest"/
        rm -rf "$tmp"
        php -r '$f=$argv[1]; $d=json_decode(file_get_contents($f), true); $d["version"]=$argv[2]; file_put_contents($f, json_encode($d, JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES));' "$dest/composer.json" "$ver"
    done

    cd ${S}
    # `composer install` (as opposed to `update`) never consults
    # composer.json's "repositories" for a package that's already in
    # composer.lock -- it downloads each locked package using the dist
    # URL recorded IN THE LOCK FILE itself (that's the whole point of a
    # lock file: resolution already happened). Confirmed the hard way:
    # an artifact-type repository, then a path-type repository, both
    # correctly configured, were both completely inert. Rewrite
    # composer.lock's own per-package "dist" directly instead, matched
    # FORWARD from the lock's own name+version to the vendor-src
    # directory this recipe's naming convention would have produced
    # (composer-vendor-<name, "/"->"-">-<version>) -- not backward via
    # the extracted zip's own internal composer.json "name", which for
    # at least one package (bnussbau/laravel-trmnl-blade, shipped by
    # upstream as "bnussbau/trmnl-blade") disagrees with what the lock
    # calls it. A ". "->"_" fallback covers gen-larapaper-vendor.py's
    # own filename sanitizing (mtdowling/jmespath.php's dir has
    # "jmespath_php", not "jmespath.php"). Force name+version into the
    # matched composer.json too, so the install's own metadata stays
    # self-consistent regardless of what the zip originally claimed.
    # ${S} is NOT re-unpacked between do_compile re-runs, so all of
    # this must be idempotent -- unconditional overwrites throughout,
    # nothing assumes anything about composer.lock's current content.
    php -r '
        $lock = json_decode(file_get_contents("composer.lock"), true);
        foreach ($lock["packages"] as &$pkg) {
            $dashed = str_replace("/", "-", $pkg["name"]);
            $candidates = [$dashed, str_replace(".", "_", $dashed)];
            $dir = null;
            foreach ($candidates as $c) {
                $try = $argv[1]."/vendor-src/composer-vendor-".$c."-".$pkg["version"];
                if (is_file($try."/composer.json")) { $dir = $try; break; }
            }
            if ($dir === null) {
                fwrite(STDERR, "no vendor-src match for {$pkg["name"]} {$pkg["version"]}\n");
                continue;
            }
            $cj = $dir."/composer.json";
            $d = json_decode(file_get_contents($cj), true);
            $d["name"] = $pkg["name"];
            $d["version"] = $pkg["version"];
            file_put_contents($cj, json_encode($d, JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES));
            $pkg["dist"] = ["type" => "path", "url" => $dir];
        }
        unset($pkg);
        file_put_contents("composer.lock", json_encode($lock, JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES));
    ' "${B}"

    export HOME="${B}/composer-home"
    export COMPOSER_HOME="${B}/composer-home"
    export COMPOSER_CACHE_DIR="${B}/composer-cache"
    php ${UNPACKDIR}/composer.phar ${LARAPAPER_COMPOSER_ARGS}
}


# Must match larapaper_0.1.bb's LARAPAPER_APP_DIR exactly -- vendor/ is
# installed as a REAL child of the app root, not a separate directory
# reached via symlink. Composer's generated autoload_static.php computes
# $baseDir as dirname(__DIR__) from vendor/composer's own physical
# (symlink-resolved) location, so if vendor/ lived elsewhere and were
# only symlinked in, App\* classes (app/Providers/AppServiceProvider.php
# etc) 404 at runtime -- confirmed the hard way on qemuarm64.
LARAPAPER_APP_DIR = "${libdir}/larapaper/app"

do_install() {
    install -d ${D}${LARAPAPER_APP_DIR}
    # A dist.url "options":{"symlink":false} on a per-PACKAGE dist entry
    # is a no-op -- composer only honors that option on a "path" TYPE
    # REPOSITORY block in composer.json, which install-from-lock never
    # consults (see do_compile). So every package here gets installed as
    # a symlink into ${B}/vendor-src regardless, and a plain `cp -r`
    # copies the symlinks themselves, not their targets -- shipping
    # dangling links into a WORKDIR that's gone by the time anything
    # runs (confirmed: larapaper-web crash-looped on a missing
    # autoload "files" entry, e.g. symfony/deprecation-contracts'
    # function.php). -L dereferences, copying real file content.
    cp -rL ${S}/vendor ${D}${LARAPAPER_APP_DIR}/vendor
    find ${D}${LARAPAPER_APP_DIR}/vendor -name "*.dist" -delete

    # vendor/composer/installed.json records each package's dist.url
    # as the vendor-src directory do_compile installed it from -- a
    # real path under this build's TMPDIR, harmless at runtime
    # (nothing reads it once autoload_*.php exist) but a QA buildpaths
    # violation as shipped. Blank it out; name/version/install-path
    # (already relative) are all this file is actually useful for.
    php -r '
        $f = $argv[1];
        $d = json_decode(file_get_contents($f), true);
        foreach ($d["packages"] as &$pkg) {
            if (($pkg["dist"]["type"] ?? "") === "path") {
                $pkg["dist"]["url"] = "";
            }
        }
        unset($pkg);
        file_put_contents($f, json_encode($d, JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES));
    ' "${D}${LARAPAPER_APP_DIR}/vendor/composer/installed.json"
}

FILES:${PN} = "${LARAPAPER_APP_DIR}/vendor"

# vendor/ is interpreted PHP source copied verbatim from each package's own
# release zip -- nothing here is built by this recipe (do_compile only runs
# composer, never a real compiler), so there's no debug info this recipe
# could have stripped and the QA check has nothing real to flag.
INSANE_SKIP:${PN} += "already-stripped"

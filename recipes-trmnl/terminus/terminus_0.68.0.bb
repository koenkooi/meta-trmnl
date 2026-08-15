SUMMARY = "Terminus, a self-hosted BYOS server for TRMNL e-ink displays"
DESCRIPTION = "Hanami/Puma web app plus a Sidekiq worker, from the same \
codebase. Installs the application source, systemd units for both \
processes, and a postgres initdb/role-create/migrate oneshot chain. \
Native-extension gems (pg, bcrypt, puma, nio4r, llhttp, redcarpet) come \
from meta-rubygems; nokogiri and websocket-driver are local bumps and \
bigdecimal/erb/json/prism/rbs/strscan are local default-gem replacements \
-- see recipes-trmnl/terminus/rubygems-*.bb."
HOMEPAGE = "https://github.com/usetrmnl/terminus"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.adoc;md5=a2f2df8f44e621fed4cc67b4289eb4b6"

SRC_URI = "git://github.com/usetrmnl/terminus.git;protocol=https;branch=main \
           file://0001-config-db-seeds-seed-a-baseline-og_plus-Model-row.patch \
           file://terminus-web.service \
           file://terminus-worker.service \
           file://terminus.env \
           file://terminus-db-init.sh \
           file://terminus-db-init.service \
           file://terminus-db-create.sh \
           file://terminus-db-create.service \
           file://terminus-db-migrate.service \
           file://terminus-npm-map.json \
           "
SRCREV = "98d4f1e6cea9c7bc47081b9b91d5426d411915c5"
PV = "0.68.0"

inherit systemd useradd

TERMINUS_APP_DIR = "${libdir}/terminus/app"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM:${PN} = "--system --home-dir ${TERMINUS_APP_DIR} --shell /bin/false terminus"

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = "terminus-web.service terminus-worker.service \
    terminus-db-init.service terminus-db-create.service terminus-db-migrate.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# The web UI's asset pipeline (Node/hanami-assets/esbuild, config/assets.js
# in the pinned source) -- the "Not packageable here" comment at the bottom
# of this file is now cleared. package-lock.json has no devDependencies at
# this SRCREV, so terminus-npm-dev.inc is genuinely empty; kept anyway,
# same shape as inker-frontend's npm split, in case that changes upstream.
include terminus-npm-prod.inc
include terminus-npm-dev.inc
DEPENDS += "nodejs-native"

# Mirrors inker-frontend_0.6.0.bb's own do_configure -- see its comment for
# why permission bits get OR'd in afterward (some published tarballs ship
# non-traversable dirs, harmless for a real `npm install`, fatal for a later
# `rm -rf` under tarfile's verbatim-preserved permissions).
python do_configure() {
    import json
    import os
    import tarfile

    unpackdir = d.getVar("UNPACKDIR")
    s = d.getVar("S")

    with open(os.path.join(unpackdir, "terminus-npm-map.json")) as f:
        pkgmap = json.load(f)

    for path, info in pkgmap.items():
        dest = os.path.join(s, path)
        bb.utils.mkdirhier(dest)
        with tarfile.open(os.path.join(unpackdir, info["file"])) as tf:
            members = []
            for m in tf.getmembers():
                parts = m.name.split("/", 1)
                if len(parts) == 2 and parts[1]:
                    m.name = parts[1]
                    members.append(m)
            tf.extractall(dest, members=members)
        for root, dirs, files in os.walk(dest):
            for name in dirs + files:
                p = os.path.join(root, name)
                if os.path.islink(p):
                    continue
                os.chmod(p, os.stat(p).st_mode | 0o700)
}

# config/assets.js (upstream, unmodified) is `import * as assets from
# "hanami-assets"; await assets.run();` -- run() with no options defaults to
# process.argv, so plain CLI flags work same as hanami-cli's own invocation,
# without needing Ruby/bundler at build time at all (hanami-assets' actual
# work is 100% in the npm package; the Ruby gem is a thin CLI wrapper).
#
# Two separate root/dest pairs, not one: hanami-assets' esbuild plugin
# writes a FRESH manifest (<dest>/assets.json) on every build-mode run, not
# a merge -- confirmed by reading dist/esbuild-plugin.js, where non-watch
# mode starts from `manifest = {}` unconditionally. Two SRC_URI roots exist
# (app/assets/, slices/authentication/assets/); a single shared --dest
# would let the second build silently wipe the first's manifest, breaking
# whichever one ran first.
#
# The slice manifest's --dest MUST be public/assets/_authentication
# (leading underscore) -- Hanami::Assets looks up a slice's manifest at
# public/assets/_<slice name>/assets.json, confirmed by the real runtime
# error root-causing the /login 500: Hanami::Assets::ManifestMissingError
# at .../public/assets/_authentication/assets.json. Writing it to
# public/assets/authentication (no underscore, the original guess) built
# successfully but was never read by anything -- silently wrong, not
# absent, which is why it went unnoticed until a live exception dump.
do_compile() {
    cd ${S}
    node config/assets.js --path=app --dest=public/assets
    node config/assets.js --path=slices/authentication --dest=public/assets/_authentication
}

do_install() {
    install -d ${D}${TERMINUS_APP_DIR}
    cp -r ${S}/. ${D}${TERMINUS_APP_DIR}/
    rm -rf ${D}${TERMINUS_APP_DIR}/.git
    # Build-time only: do_compile already consumed this into public/assets/.
    # Shipping it would add 48 npm packages' worth of dead weight to every
    # installed image for zero runtime benefit.
    rm -rf ${D}${TERMINUS_APP_DIR}/node_modules
    install -d ${D}${TERMINUS_APP_DIR}/log \
               ${D}${TERMINUS_APP_DIR}/tmp \
               ${D}${TERMINUS_APP_DIR}/public/assets \
               ${D}${TERMINUS_APP_DIR}/public/uploads/cache

    install -d ${D}${sysconfdir}/terminus
    install -m 0644 ${UNPACKDIR}/terminus.env ${D}${sysconfdir}/terminus/terminus.env

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/terminus-web.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/terminus-worker.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/terminus-db-init.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/terminus-db-create.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/terminus-db-migrate.service ${D}${systemd_system_unitdir}/

    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/terminus-db-init.sh ${D}${libexecdir}/
    install -m 0755 ${UNPACKDIR}/terminus-db-create.sh ${D}${libexecdir}/
}

# Gemfile.lock pins nokogiri as 8 precompiled per-platform stanzas, no plain
# "ruby" platform entry -- rubygems-nokogiri_1.19.4.bb ships the ruby-platform
# source gem instead, which Bundler would otherwise reject outright. Collapse
# the 8 stanzas/checksums into one and add "ruby" to PLATFORMS. Name-anchored
# and fails loudly (bbfatal) if a future SRCREV changes the lock's shape.
do_install:append () {
    lock="${D}${TERMINUS_APP_DIR}/Gemfile.lock"
    grep -q '^    nokogiri (1.19.4)$' "$lock" && return 0

    awk '
        /^    nokogiri \(1\.19\.4-/ {
            n++
            if (n == 1) { print "    nokogiri (1.19.4)"; print "      racc (~> 1.4)" }
            getline
            next
        }
        { print }
        END { if (n == 0) exit 1 }
    ' "$lock" > "$lock.new" \
        && mv "$lock.new" "$lock" \
        || bbfatal "Gemfile.lock: no nokogiri platform spec stanzas found -- lock format changed?"

    awk '
        /^  nokogiri \(1\.19\.4-.*\) sha256=/ {
            n++
            if (n == 1) print "  nokogiri (1.19.4) sha256=50c951611c92bca05c51411aef45f1cbc50f2821c4802758c5c6d34696533ab5"
            next
        }
        { print }
        END { if (n == 0) exit 1 }
    ' "$lock" > "$lock.new" \
        && mv "$lock.new" "$lock" \
        || bbfatal "Gemfile.lock CHECKSUMS: no nokogiri platform entries found -- lock format changed?"

    awk '
        { print }
        /^  arm64-darwin$/ { print "  ruby"; found=1 }
        END { if (!found) exit 1 }
    ' "$lock" > "$lock.new" \
        && mv "$lock.new" "$lock" \
        || bbfatal "Gemfile.lock PLATFORMS: arm64-darwin anchor not found -- lock format changed?"
}

FILES:${PN} = "${TERMINUS_APP_DIR} ${sysconfdir}/terminus ${systemd_system_unitdir} ${libexecdir}"

# racc is the one entry here still satisfied by ruby itself (meta-rubygems'
# recipe is a deliberately empty meta-package) -- everything else in this
# list resolves to a real gem at the Gemfile.lock version via the local
# rubygems-*.bb recipes in this directory.
# imagemagick: mini_magick shells out to it from every screen render, see
# app/aspects/screens/converters/{color,monochrome}.rb.
# postgresql-contrib: the very first migration (config/db/migrate/
# 20250916111723_add_citext_extension.rb) runs CREATE EXTENSION citext,
# whose citext.so lives in postgresql-contrib, not the base postgresql
# package -- without it terminus-db-migrate.service fails fast (~16s,
# PG::UndefinedFile: could not access file "$libdir/citext") and the
# schema never gets created, which is what actually crash-loops Puma in
# Hanami::Providers::DB#start -> ROM::SQL::Schema#finalize_attributes!,
# not a migration-is-just-slow timing issue.
RDEPENDS:${PN} = "ruby ruby-terminus-gems \
    rubygems-nokogiri rubygems-websocket-driver \
    rubygems-bigdecimal rubygems-json rubygems-strscan rubygems-racc \
    rubygems-prism rubygems-rbs rubygems-erb \
    rubygems-bcrypt rubygems-llhttp rubygems-nio4r rubygems-pg \
    rubygems-puma rubygems-redcarpet \
    postgresql postgresql-client postgresql-contrib imagemagick \
    chromium-terminus-config \
"

# chromium-terminus-config pulls in meta-browser's chromium-ozone-wayland
# (a real aarch64 build, ~377M installed) and points ferrum's BROWSER_PATH
# at it. Every device-facing screen render reaches Ferrum::Browser.new via
# app/aspects/screens/temp_pather.rb, so without it /api/display 500s.

pkg_postinst:${PN}:append () {
    chown -R terminus:terminus $D${TERMINUS_APP_DIR}/log \
        $D${TERMINUS_APP_DIR}/tmp $D${TERMINUS_APP_DIR}/public
}

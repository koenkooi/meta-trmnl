SUMMARY = "Inker frontend, the React/Vite web UI for the Inker BYOS server"
DESCRIPTION = "Static Vite/React 19 build -- vite bundles the entire \
dependency graph into dist/, so nothing here is a runtime dependency \
of the target image, only a build-time one (Node/npm to run `vite \
build`, same npm-fetcher-disabled/vendor-via-SRC_URI story as \
inker-backend, see its recipe header). Skips `tsc -b`'s project- \
reference type-check that upstream's own build script runs first \
(advisory only in this workflow -- vite's own esbuild-based transpile \
is what actually has to succeed to produce a working bundle) and goes \
straight to `vite build`. Installs dist/, plus an nginx site \
(inker-nginx.site) that mirrors upstream's own docker/nginx.conf -- \
same map/log_format/location/proxy_pass rules, root pointed at this \
recipe's own install path and marked default_server instead of \
docker's single-container assumption. pkg_postinst disables \
meta-webserver's stock default_server site so this one answers on \
port 80 unconditionally."
HOMEPAGE = "https://github.com/usetrmnl/inker"
LICENSE = "AGPL-3.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/AGPL-3.0-only;md5=73f1eb20517c55bf9493b7dd6e480788"

SRC_URI = "git://github.com/usetrmnl/inker.git;protocol=https;branch=main;subpath=frontend \
           file://inker-frontend-npm-map.json \
           file://inker-nginx.site \
           "
SRCREV = "83c72b0c590cca40df9da1c646c3d5693e0028df"
PV = "0.6.0"

# see inker-backend_0.6.0.bb's own S comment -- same subpath mechanics.
S = "${UNPACKDIR}/frontend"

include inker-frontend-npm-prod.inc
include inker-frontend-npm-dev.inc

DEPENDS = "nodejs-native"

python do_configure() {
    import json
    import os
    import tarfile

    unpackdir = d.getVar("UNPACKDIR")
    s = d.getVar("S")

    with open(os.path.join(unpackdir, "inker-frontend-npm-map.json")) as f:
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
        # Some npm packages ship files/dirs with odd owner permission
        # bits in their published tarball (pngjs's lib/ and coverage/
        # ship as mode 0o622, no execute/search bit at all) --
        # harmless for a real `npm install` (npm normalizes
        # permissions), but tarfile preserves them verbatim, and a
        # later plain `rm -rf` (do_rm_work, or bitbake's own do_unpack
        # re-run) can't even traverse into them, let alone unlink.
        # OR in full owner rwx unconditionally -- never removes an
        # existing permission, just guarantees traversal/removal works.
        for root, dirs, files in os.walk(dest):
            for name in dirs + files:
                p = os.path.join(root, name)
                if os.path.islink(p):
                    continue
                os.chmod(p, os.stat(p).st_mode | 0o700)
}

do_compile() {
    cd ${S}
    node node_modules/vite/bin/vite.js build
}

do_install() {
    install -d ${D}${libdir}/inker-frontend
    cp -r ${S}/dist ${D}${libdir}/inker-frontend/dist

    install -d ${D}${sysconfdir}/nginx/sites-available
    install -m 0644 ${UNPACKDIR}/inker-nginx.site ${D}${sysconfdir}/nginx/sites-available/inker
    sed -i 's,@INKER_FRONTEND_DIST@,${libdir}/inker-frontend/dist,' ${D}${sysconfdir}/nginx/sites-available/inker
    install -d ${D}${sysconfdir}/nginx/sites-enabled
    ln -sf ../sites-available/inker ${D}${sysconfdir}/nginx/sites-enabled/inker
}

FILES:${PN} = " \
    ${libdir}/inker-frontend \
    ${sysconfdir}/nginx/sites-available/inker \
    ${sysconfdir}/nginx/sites-enabled/inker \
"

RDEPENDS:${PN} += "nginx"

# The stock default_server site meta-webserver's nginx ships also
# claims "listen 80 default_server" -- two default_server directives
# on the same address:port is a hard nginx startup error. Disable it
# rather than editing another package's file at do_install time.
pkg_postinst:${PN} () {
    rm -f $D${sysconfdir}/nginx/sites-enabled/default_server
}

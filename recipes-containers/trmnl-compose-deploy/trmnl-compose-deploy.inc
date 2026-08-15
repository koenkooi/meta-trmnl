# Shared logic for every per-image *-compose-deploy recipe. Each concrete
# recipe (terminus-compose-deploy_1.0.bb, inker-compose-deploy_1.0.bb, ...)
# requires this file plus its own <app>-apps.inc (from trmnl-oci-import,
# reused here for TRMNL_COMPOSE_APP) then adds SUMMARY/DESCRIPTION itself.
#
# This package ships ONLY the shared systemd template unit
# (trmnl-compose@.service) and script (trmnl-compose.sh) -- tiny, genuinely
# package-shaped content, mirroring trmnl-oci-import's split exactly. The
# compose files themselves do NOT go through opkg: they are written straight
# into IMAGE_ROOTFS by the image recipes (see trmnl-oci-tars.inc for the
# same reasoning), and because they must be generated from the image
# recipe's own TERMINUS_APP_DIR/TERMINUS_VALKEY_DATA_DIR.
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require trmnl-deploy-method.inc

SRC_URI = "file://trmnl-compose@.service file://trmnl-compose.sh"

inherit systemd

# Deliberately no branch on TRMNL_DEPLOY_METHOD's value here: keeping the
# switch out of this recipe's signature means flipping it never rebuilds
# this package, only the image (CORE_IMAGE_EXTRA_INSTALL) that decides
# whether it's installed at all. The require above only supplies the
# default -- it does not make this recipe's own tasks depend on the value.
SYSTEMD_SERVICE:${PN} = "trmnl-compose@${TRMNL_COMPOSE_APP}.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

FILES:${PN} += "\
    ${systemd_system_unitdir}/trmnl-compose@.service \
    ${bindir}/trmnl-compose.sh \
"

# podman-compose, not podman alone: trmnl-compose.sh execs `docker
# compose`, and podman only resolves that subcommand to something real
# (podman-compose or docker-compose) if one of them is actually installed
# -- meta-virtualization ships podman-compose 1.5.0.
#
# Hardcoded, not resolved through meta-virtualization's virtual/ providers,
# because there is nothing to resolve through: meta-virtualization only
# defines virtual/runc, virtual/containerd and virtual/docker (-> docker-
# moby) -- podman itself carries no PROVIDES, and neither compose
# implementation does either. TRMNL_DEPLOY_METHOD = "docker" does NOT
# currently install a real docker engine; it still runs podman underneath
# via podman-compose (see README's Quadlet-or-compose section) -- podman's
# own package already ships a `docker` command (PODMAN_FEATURES defaults
# to "docker"), which is what makes trmnl-compose.sh's `docker compose`
# invocation resolve at all today. Adopting a genuine docker-moby engine
# here means switching this to "virtual/docker docker-compose" -- a real,
# currently-untested change that needs its own build and boot/screenshot
# verification, not a drop-in rename.
RDEPENDS:${PN} = "podman podman-compose"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/trmnl-compose@.service ${D}${systemd_system_unitdir}/trmnl-compose@.service

    install -d ${D}${bindir}
    install -m 0755 ${UNPACKDIR}/trmnl-compose.sh ${D}${bindir}/trmnl-compose.sh
}

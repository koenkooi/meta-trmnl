# Shared logic for every per-image *-oci-import recipe. Each concrete
# recipe (trmnl-oci-import_1.0.bb for Terminus, inker-oci-import_1.0.bb
# for Inker, ...) requires this file plus its own <app>-apps.inc setting
# TRMNL_OCI_APPS, then adds SUMMARY/DESCRIPTION/LICENSE itself.
#
# This package ships ONLY the shared systemd template unit
# (trmnl-oci-import@.service) and script (trmnl-oci-import.sh) -- tiny,
# genuinely package-shaped content. The OCI tar archives themselves
# (hundreds of MB each) do NOT go through opkg at all: see
# trmnl-oci-tars.inc, required directly by each consuming *-container-
# image.bb, which copies them straight into IMAGE_ROOTFS via
# IMAGE_PREPROCESS_COMMAND. opkg/.ipk is a package format sized for
# ordinary software packages, not multi-hundred-MB binary blobs -- wrapping
# them in it was masking real staleness under a size-adjacent class of bug
# that cost a very long debugging session to even identify (meta-trmnl
# TODO.md). Never revert this split back to packaging the tars.
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://trmnl-oci-import@.service file://trmnl-oci-import.sh"

inherit systemd

SYSTEMD_SERVICE:${PN} = "${@' '.join('trmnl-oci-import@%s.service' % p.split(':')[0] for p in (d.getVar('TRMNL_OCI_APPS') or '').split())}"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

FILES:${PN} += "\
    ${systemd_system_unitdir}/trmnl-oci-import@.service \
    ${bindir}/trmnl-oci-import.sh \
"

# openssl-bin: sha256 for the content-addressed import guard (no coreutils
# on these images, busybox has no CONFIG_SHA256SUM). util-linux-flock: the
# import lock -- both pinned explicitly rather than relied on as transitive
# deps of something else.
RDEPENDS:${PN} = "podman openssl-bin util-linux-flock"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/trmnl-oci-import@.service ${D}${systemd_system_unitdir}/trmnl-oci-import@.service

    install -d ${D}${bindir}
    install -m 0755 ${UNPACKDIR}/trmnl-oci-import.sh ${D}${bindir}/trmnl-oci-import.sh
}

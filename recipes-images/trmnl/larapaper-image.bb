SUMMARY = "Baremetal qemuarm64 test image for meta-trmnl's LaraPaper server"
DESCRIPTION = "Phase 1 test image: PHP + the Composer vendor closure + \
LaraPaper installed directly into the rootfs (no image-oci.bbclass \
layering yet -- that's Phase 2, see meta-trmnl/README.md). No target \
Chromium/Puppeteer yet -- exists to prove the Laravel app itself \
installs and boots under qemuarm64, not for real deployment."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require recipes-images/trmnl/trmnl-console-base.inc

export IMAGE_BASENAME = "larapaper-image"

CORE_IMAGE_EXTRA_INSTALL += " \
    php-cli \
    php-imagick \
    larapaper-vendor \
    larapaper \
"

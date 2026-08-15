SUMMARY = "Baremetal qemuarm64 test image for meta-trmnl BYOS servers"
DESCRIPTION = "Phase 1 test image: PostgreSQL + Valkey + Ruby + the \
Terminus app installed directly into the rootfs (no image-oci.bbclass \
layering yet -- that's Phase 2, see meta-trmnl/README.md). Exists to \
boot-test the packaging work under qemuarm64, not for real deployment."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require recipes-images/trmnl/trmnl-console-base.inc

export IMAGE_BASENAME = "trmnl-image"

CORE_IMAGE_EXTRA_INSTALL += " \
    postgresql postgresql-client \
    valkey \
    ruby \
    ruby-terminus-gems \
    terminus \
    curl \
"

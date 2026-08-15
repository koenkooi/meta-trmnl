SUMMARY = "Baremetal qemuarm64 test image for meta-trmnl's Inker server"
DESCRIPTION = "Test image: Node.js + the Inker backend (NestJS API, \
SQLite via Prisma) installed directly into the rootfs, the built \
Inker frontend static assets, and nginx serving the frontend bundle \
on port 80 while reverse-proxying /api, /assets, /uploads to the \
backend on 3002 (see inker-frontend's own nginx site). No \
image-oci.bbclass layering yet, see meta-trmnl/README.md. Exists to \
boot-test the packaging work under qemuarm64, not for real \
deployment."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require recipes-images/trmnl/trmnl-console-base.inc

export IMAGE_BASENAME = "inker-image"

CORE_IMAGE_EXTRA_INSTALL += " \
    nodejs \
    inker-backend \
    inker-frontend \
    nginx \
    curl \
"

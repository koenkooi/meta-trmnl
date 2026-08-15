SUMMARY = "Inker backend as a single-process image-oci.bbclass container"
DESCRIPTION = "Wraps inker-backend (recipes-trmnl/inker/inker-backend_0.6.0.bb) \
in meta-virtualization's container-base.bb shape -- no systemd inside the \
container, matching upstream's own docker entrypoint idiom (see the \
'Design notes for Phase 2' section of meta-trmnl/README.md). The entrypoint \
folds inker-backend.service's ExecStartPre chain (state-dir mkdir, prisma \
db push, seed) in front of `exec node dist/main.js`. State (sqlite DB, \
uploads, logs) lives in the inker-data.volume Quadlet volume mounted at \
/var/lib/inker -- see recipes-images/trmnl/inker-container-image.bb, which \
installs the .container/.volume units this image is run under."
HOMEPAGE = "https://github.com/usetrmnl/inker"
LICENSE = "AGPL-3.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/AGPL-3.0-only;md5=73f1eb20517c55bf9493b7dd6e480788"

require recipes-images/trmnl/trmnl-container-base.bb
TRMNL_CONTAINER_TITLE = "inker-backend"

IMAGE_INSTALL:append = " nodejs inker-backend"

IMAGE_PREPROCESS_COMMAND += "inker_backend_container_entrypoint"
inker_backend_container_entrypoint () {
    install -d ${IMAGE_ROOTFS}${libexecdir}
    cat > ${IMAGE_ROOTFS}${libexecdir}/inker-backend-entrypoint.sh << 'EOF'
#!/bin/sh
# Folds inker-backend.service's ExecStartPre chain in front of the app --
# no systemd inside this container, see the recipe header.
set -e
cd ${libdir}/inker-backend/app
mkdir -p /var/lib/inker/uploads/screens /var/lib/inker/uploads/firmware \
         /var/lib/inker/uploads/widgets /var/lib/inker/uploads/captures \
         /var/lib/inker/uploads/drawings /var/lib/inker/logs
node ./node_modules/prisma/build/index.js db push --skip-generate || true
node ./prisma/seed.js || true
exec node dist/main.js
EOF
    chmod 0755 ${IMAGE_ROOTFS}${libexecdir}/inker-backend-entrypoint.sh
}

OCI_IMAGE_ENTRYPOINT = "${libexecdir}/inker-backend-entrypoint.sh"
OCI_IMAGE_WORKINGDIR = "${libdir}/inker-backend/app"
OCI_IMAGE_PORTS = "3002/tcp"

# Verbatim copy of inker-backend.env (recipes-trmnl/inker/inker-backend/
# inker-backend.env) -- OCI_IMAGE_ENV_VARS is a plain whitespace-split list,
# so no value here may contain a space.
OCI_IMAGE_ENV_VARS = "\
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    NODE_ENV=production \
    PORT=3002 \
    DATABASE_URL=file:/var/lib/inker/inker.db \
    SCREENS_DIR=/var/lib/inker/uploads/screens \
    FIRMWARE_DIR=/var/lib/inker/uploads/firmware \
    HOME=${libdir}/inker-backend/app \
    PRISMA_QUERY_ENGINE_LIBRARY=${libdir}/inker-backend/app/prisma-engines/libquery_engine.so.node \
    PRISMA_SCHEMA_ENGINE_BINARY=${libdir}/inker-backend/app/prisma-engines/schema-engine \
    PRISMA_ENGINES_CHECKSUM_IGNORE_MISSING=1 \
"

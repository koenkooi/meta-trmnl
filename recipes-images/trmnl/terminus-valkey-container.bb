SUMMARY = "Valkey for Terminus as a single-process image-oci.bbclass container"
DESCRIPTION = "No systemd inside the container -- see the 'Design notes for \
Phase 2' section of meta-trmnl/README.md. The container starts as root and \
its own entrypoint chowns /data before dropping to the valkey user via \
setpriv, exec'ing valkey-server as PID 1 -- same shape as \
terminus-postgres-container.bb's entrypoint. Does NOT rely on the \
orchestrator's copy-up/volume-seeding behaviour to get ownership right: \
that differs between podman Quadlet and podman-compose (verified for real --\
compose's own volume creation left /data root-owned, valkey failed with \
'Permission denied' opening its RDB temp file), so this chowns explicitly \
at container startup regardless of which orchestrator mounted the volume."
HOMEPAGE = "https://valkey.io/"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/BSD-3-Clause;md5=550794465ba0ec5312d6919e203a55f9"

require recipes-images/trmnl/trmnl-container-base.bb
TRMNL_CONTAINER_TITLE = "terminus-valkey"

IMAGE_INSTALL:append = " valkey util-linux-setpriv"

# --dir: where valkey-server's own RDB/AOF snapshots land -- must match
# terminus-container-image.bb's TERMINUS_VALKEY_DATA_DIR, which mounts a
# persistent volume there. --save: enable periodic snapshotting explicitly
# rather than rely on valkey's own compiled-in default, which this build
# has not verified.
VALKEY_DATA_DIR = "/data"
IMAGE_PREPROCESS_COMMAND += "terminus_valkey_container_entrypoint"
terminus_valkey_container_entrypoint () {
    install -d ${IMAGE_ROOTFS}${libexecdir}
    cat > ${IMAGE_ROOTFS}${libexecdir}/terminus-valkey-entrypoint.sh << EOF
#!/bin/sh
set -e
DATA_DIR=${VALKEY_DATA_DIR}

# Runs every start, not just first: cheap, idempotent, and the volume may
# have been (re)created by either orchestrator's own volume-seeding
# behaviour, which this deliberately does not trust to have gotten
# ownership right (see DESCRIPTION).
mkdir -p "\$DATA_DIR"
chown valkey:valkey "\$DATA_DIR"

exec setpriv --reuid=valkey --regid=valkey --init-groups \\
    valkey-server --daemonize no --bind 0.0.0.0 --port 6379 --dir "\$DATA_DIR" --save 60 1
EOF
    chmod 0755 ${IMAGE_ROOTFS}${libexecdir}/terminus-valkey-entrypoint.sh
}

OCI_IMAGE_ENTRYPOINT = "${libexecdir}/terminus-valkey-entrypoint.sh"
OCI_IMAGE_PORTS = "6379/tcp"

# The image's own baked-in /data (owned by valkey:valkey) is still useful
# as the seed a fresh copy-up volume starts from, even though the
# entrypoint above no longer depends on that ownership surviving.
ROOTFS_POSTPROCESS_COMMAND += "terminus_valkey_data_dir"
terminus_valkey_data_dir () {
    install -d ${IMAGE_ROOTFS}${VALKEY_DATA_DIR}
    chown valkey:valkey ${IMAGE_ROOTFS}${VALKEY_DATA_DIR}
}

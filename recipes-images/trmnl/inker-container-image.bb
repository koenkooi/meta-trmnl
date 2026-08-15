SUMMARY = "Baremetal qemuarm64 test image for running Inker as podman/Quadlet containers"
DESCRIPTION = "podman + Quadlet .container/.volume units for the two \
image-oci.bbclass builds (recipes-images/trmnl/inker-{backend,frontend}- \
container.bb), proving they actually run instead of just building. Does \
NOT install inker-backend/inker-frontend/nginx directly into the rootfs \
-- that is inker-image.bb's job (baremetal). The *-oci.tar archives are \
baked into this rootfs at build time via inker-oci-import (see \
recipes-containers/trmnl-oci-import), which imports each into podman at \
boot with no network fetch involved -- the two Quadlet units then start \
once their matching inker-oci-import@ instance has tagged the image. \
Replaces the earlier in-guest TFTP + podman pull mechanism (see git \
history), same design as terminus-container-image.bb."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require recipes-images/trmnl/trmnl-console-base.inc

export IMAGE_BASENAME = "inker-container-image"

require recipes-containers/trmnl-oci-import/inker-apps.inc
require recipes-containers/trmnl-oci-import/trmnl-oci-tars.inc
require recipes-containers/trmnl-compose-deploy/trmnl-deploy-method.inc

CORE_IMAGE_EXTRA_INSTALL += " \
    podman \
    curl \
    inker-oci-import \
"

# inker-compose-deploy (recipes-containers/trmnl-compose-deploy, the
# shared trmnl-compose-deploy.inc's thin per-app recipe -- there is no
# package literally named "trmnl-compose-deploy") carries
# trmnl-compose@.service, the driver this app's compose.yml runs under;
# podman-compose is the provider `podman compose` shells out to. Read
# TRMNL_DEPLOY_METHOD directly (never a derived variable) so this stays a
# dependency of the expanded value, not just of some intermediate -- see
# recipes-containers/trmnl-compose-deploy/trmnl-deploy-method.inc.
CORE_IMAGE_EXTRA_INSTALL += "${@'inker-compose-deploy podman-compose' if d.getVar('TRMNL_DEPLOY_METHOD') == 'docker' else ''}"

# meta-virtualization's own container-host-config storage.conf deliberately
# pins driver = "vfs" (not overlay) distro-wide -- "avoids permission issues
# when deploying pre-built containers during Yocto image build (under
# pseudo/fakeroot)", see that file's own comment. VFS has no copy-on-write:
# every pulled layer is a full directory copy, so backend's ~150MB compressed
# oci.tar (mostly node_modules) alone exhausted the default rootfs margin on
# the very first `podman pull`, before frontend or either container's own
# writable layer. 4GB is real headroom for both images' layers plus restart
# churn, not a tuned minimum -- this is a test image, disk is cheap. Kept
# the same after the TFTP->rootfs-bake conversion: the archives now also
# live baked into the rootfs itself (~165MB combined, much smaller than
# Terminus's four), well inside this existing margin.
IMAGE_ROOTFS_EXTRA_SPACE = "4194304"

# Quadlet units are always installed, whatever TRMNL_DEPLOY_METHOD says --
# the image they reference is baked into the rootfs and imported by its
# matching inker-oci-import@<instance>.service (see inker-apps.inc above).
# Only their [Install] section (appended conditionally at the end of this
# function) decides whether podman-system-generator actually starts them
# at boot, so the podman-native path stays reachable via a manual
# `systemctl start` either way.
IMAGE_PREPROCESS_COMMAND += "inker_container_quadlet_units "
inker_container_quadlet_units () {
    install -d ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/inker-data.volume << 'EOF'
[Volume]
VolumeName=trmnl-inker-data
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/inker-backend.container << 'EOF'
[Unit]
Description=Inker backend (podman, image-oci.bbclass build)
# The shared systemd unit template (trmnl-oci-import.inc) is always named
# trmnl-oci-import@.service regardless of which per-image *-oci-import
# package installs it -- only the bitbake PN differs (inker-oci-import
# here), never the unit file itself. Referencing "inker-oci-import@..."
# here would point at a unit that doesn't exist.
After=trmnl-oci-import@inker-backend.service
Requires=trmnl-oci-import@inker-backend.service

[Container]
Image=localhost/trmnl-inker-backend:latest
ContainerName=inker-backend
Network=host
Volume=inker-data.volume:/var/lib/inker

[Service]
Restart=on-failure
RestartSec=5
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/inker-frontend.container << 'EOF'
[Unit]
Description=Inker frontend (podman, image-oci.bbclass build)
After=inker-backend.service trmnl-oci-import@inker-frontend.service
Requires=inker-backend.service trmnl-oci-import@inker-frontend.service

[Container]
Image=localhost/trmnl-inker-frontend:latest
ContainerName=inker-frontend
Network=host

[Service]
Restart=on-failure
RestartSec=5
EOF

    # With TRMNL_DEPLOY_METHOD = "docker", trmnl-compose@inker.service owns
    # these containers. The Quadlet units stay installed -- `systemctl
    # start inker-backend` still works after stopping compose -- but
    # nothing pulls them in at boot, so the two paths can never run the
    # same container, bind the same port, or write the same volume
    # concurrently.
    if ${@'false' if d.getVar('TRMNL_DEPLOY_METHOD') == 'docker' else 'true'}; then
        for unit in inker-backend inker-frontend; do
            cat >> ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/${unit}.container << 'EOF'

[Install]
WantedBy=multi-user.target
EOF
        done
    fi
}

# The compose file is cheap and useful to read on a device; only written
# (and only wired to start at boot) when TRMNL_DEPLOY_METHOD = "docker" --
# compose and Quadlet must never own the same containers, see
# inker_container_quadlet_units's [Install] loop above.
IMAGE_PREPROCESS_COMMAND += "${@'inker_container_compose_units ' if d.getVar('TRMNL_DEPLOY_METHOD') == 'docker' else ''}"
inker_container_compose_units () {
    install -d ${IMAGE_ROOTFS}${datadir}/trmnl/compose/inker

    cat > ${IMAGE_ROOTFS}${datadir}/trmnl/compose/inker/compose.yml << 'EOF'
# Generated by inker-container-image.bb -- do not edit on the target.
# Same topology as the Quadlet units in /etc/containers/systemd; run by
# trmnl-compose@inker.service. See meta-trmnl/TODO.md.
#
# Deliberately close to upstream's own docker-compose.yml
# (github.com/usetrmnl/inker, root of repo) in shape -- restart policy,
# healthcheck-gated depends_on -- but NOT identical:
# - image: points at our own baked, already-imported localhost/trmnl-*
#   images (pull_policy: never), not a local `build: .` -- and there are
#   two of them (inker-backend, inker-frontend), matching this layer's
#   split packaging, not upstream's single monolithic container.
# - No ports:/ADMIN_PIN/TZ environment: block: baked into each image's own
#   env file for Network=host, matching the Quadlet units.
# - healthcheck test commands are a plain /dev/tcp reachability probe
#   (POSIX sh, no assumption about bun or curl being present in either
#   image), not upstream's bun-specific /health fetch -- that endpoint
#   lives in the monolith's combined process, not cleanly reachable the
#   same way once split into two containers.
name: trmnl-inker

services:
  inker-backend:
    image: localhost/trmnl-inker-backend:latest
    pull_policy: never
    container_name: inker-backend
    network_mode: host
    restart: unless-stopped
    volumes:
      - trmnl-inker-data:/var/lib/inker
    healthcheck:
      # /dev/tcp is a bashism this image's busybox ash doesn't support
      # (confirmed live: "nonexistent directory"). /health is a real,
      # confirmed-responding endpoint on this container directly (same
      # path upstream's own monolith healthcheck uses).
      test: ["CMD-SHELL", "wget -q -O /dev/null --timeout=3 http://127.0.0.1:3002/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 15s

  inker-frontend:
    image: localhost/trmnl-inker-frontend:latest
    pull_policy: never
    container_name: inker-frontend
    network_mode: host
    restart: unless-stopped
    depends_on:
      inker-backend:
        condition: service_healthy
    healthcheck:
      # Same /dev/tcp fix as inker-backend above. Root path is the real
      # PIN-entry dashboard this project has already screenshot-verified.
      test: ["CMD-SHELL", "wget -q -O /dev/null --timeout=3 http://127.0.0.1/"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 10s

volumes:
  trmnl-inker-data:
    name: trmnl-inker-data
EOF

    # trmnl-compose@inker.service needs every image this app uses already
    # tagged into podman first -- same requirement the Quadlet units
    # express via After=/Requires= on trmnl-oci-import@<instance>.service.
    install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/trmnl-compose@inker.service.d
    deps=""
    for pair in ${TRMNL_OCI_APPS}; do
        deps="${deps} trmnl-oci-import@${pair%%:*}.service"
    done
    deps="${deps# }"
    cat > ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/trmnl-compose@inker.service.d/order.conf << EOF
[Unit]
After=${deps}
Requires=${deps}
EOF
}

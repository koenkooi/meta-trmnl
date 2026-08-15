SUMMARY = "Baremetal qemuarm64 test image for running Terminus as podman/Quadlet containers"
DESCRIPTION = "podman + Quadlet .container/.volume units for the four \
image-oci.bbclass builds (recipes-images/trmnl/terminus-{postgres,valkey, \
web,worker}-container.bb), proving they actually run instead of just \
building. Does NOT install terminus/postgresql/valkey directly into the \
rootfs -- that is trmnl-image.bb's job (baremetal). The *-oci.tar archives \
are baked into this rootfs at build time via trmnl-oci-import (see \
recipes-containers/trmnl-oci-import), which imports each into podman at \
boot with no network fetch involved -- the four Quadlet units then start \
once their matching trmnl-oci-import@ instance has tagged the image. See \
meta-trmnl/README.md's Phase 2 section and \
recipes-images/trmnl/inker-container-image.bb, the structural template \
this recipe follows (still on the older in-guest TFTP+pull mechanism)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require recipes-images/trmnl/trmnl-console-base.inc

export IMAGE_BASENAME = "terminus-container-image"

require recipes-containers/trmnl-oci-import/terminus-apps.inc
require recipes-containers/trmnl-oci-import/trmnl-oci-tars.inc
require recipes-containers/trmnl-compose-deploy/trmnl-deploy-method.inc

# Matches terminus_0.68.0.bb/terminus-web-container.bb/terminus-worker-
# container.bb's own definition -- used below only to name the Quadlet
# volume mount points, not to install anything at this path itself.
TERMINUS_APP_DIR = "${libdir}/terminus/app"

CORE_IMAGE_EXTRA_INSTALL += " \
    podman \
    curl \
    trmnl-oci-import \
"

# podman-compose is podman's own shim's actual provider for `podman
# compose` -- without a compose binary in the rootfs the shim has nothing
# to exec. terminus-compose-deploy (recipes-containers/trmnl-compose-deploy,
# the shared trmnl-compose-deploy.inc's thin per-app recipe -- there is no
# package literally named "trmnl-compose-deploy") ships the
# trmnl-compose@.service template and helper script. Neither is needed
# with TRMNL_DEPLOY_METHOD at its default -- see
# recipes-containers/trmnl-compose-deploy/trmnl-deploy-method.inc.
CORE_IMAGE_EXTRA_INSTALL += "${@'terminus-compose-deploy podman-compose' if d.getVar('TRMNL_DEPLOY_METHOD') == 'docker' else ''}"

# Same VFS-driver space pressure as inker-container-image.bb (meta-virtualization's
# container-host-config storage.conf pins driver=vfs, no copy-on-write): no
# layer sharing between the four baked-in archives and their unpacked podman
# storage. Generous headroom, not a measured minimum -- roughly 2.7x the
# previous 6291456 (6GiB) budget.
IMAGE_ROOTFS_EXTRA_SPACE = "16777216"

# Each .container unit now has a real [Install] section: the image it
# references is baked into the rootfs and imported by its matching
# trmnl-oci-import@<instance>.service (see TRMNL_OCI_APPS above), so unlike
# the old in-guest-pull design there is no reason to leave these unit files
# unstarted at boot.
IMAGE_PREPROCESS_COMMAND += "terminus_container_quadlet_units"
terminus_container_quadlet_units () {
    # trmnl-oci-import@.service (recipes-containers/trmnl-oci-import) is
    # app-agnostic and has no ordering between instances -- all four of
    # this image's instances are pulled in independently by their matching
    # Quadlet unit's Requires=, so with no ordering imposed here they all
    # start at once and race for that script's single mkdir lock, each
    # loser spin-waiting (sleep 0.2) until the winner finishes its podman
    # pull/extract. Observed directly on a live boot: valkey (6MB) wins and
    # finishes in ~6s, but postgres/web/worker then contend, and with the
    # two large images (web/worker, 345MB each) able to win ahead of
    # postgres, postgres's own boot-validate check can end up waiting
    # behind ~700MB of unrelated imports. Chain them into a fixed order
    # instead, smallest first, so only one import ever runs at a time and
    # total worst-case wait is deterministic and bounded.
    for pair in \
        "terminus-postgres:terminus-valkey" \
        "terminus-web:terminus-postgres" \
        "terminus-worker:terminus-web" \
    ; do
        inst="${pair%%:*}"
        after="${pair##*:}"
        install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/trmnl-oci-import@${inst}.service.d
        cat > ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/trmnl-oci-import@${inst}.service.d/order.conf << EOF
[Unit]
After=trmnl-oci-import@${after}.service
EOF
    done

    install -d ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-pgdata.volume << 'EOF'
[Volume]
VolumeName=trmnl-terminus-pgdata
EOF

    # valkey and the web/worker uploads+fonts dirs previously had no
    # persistent volume at all -- valkey's RDB/AOF snapshots and every
    # upload/synced font lived only in each container's own writable
    # layer, gone on any container recreation (and, for uploads/fonts
    # specifically, web and worker each have their OWN writable layer, so
    # a file one wrote was never visible to the other even before that).
    # See meta-trmnl TODO.md's containerization design section.
    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-keyvalue.volume << 'EOF'
[Volume]
VolumeName=trmnl-terminus-keyvalue
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-uploads.volume << 'EOF'
[Volume]
VolumeName=trmnl-terminus-uploads
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-fonts.volume << 'EOF'
[Volume]
VolumeName=trmnl-terminus-fonts
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-postgres.container << 'EOF'
[Unit]
Description=Terminus postgres (podman, image-oci.bbclass build)
After=trmnl-oci-import@terminus-postgres.service
Requires=trmnl-oci-import@terminus-postgres.service

[Container]
Image=localhost/trmnl-terminus-postgres:latest
ContainerName=terminus-postgres
Network=host
Volume=terminus-pgdata.volume:/var/lib/postgresql/data

[Service]
Restart=on-failure
RestartSec=5
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-valkey.container << EOF
[Unit]
Description=Terminus valkey (podman, image-oci.bbclass build)
After=trmnl-oci-import@terminus-valkey.service
Requires=trmnl-oci-import@terminus-valkey.service

[Container]
Image=localhost/trmnl-terminus-valkey:latest
ContainerName=terminus-valkey
Network=host
# No User= here anymore: the image's own entrypoint now starts as root,
# chowns the mounted volume, then drops to valkey via setpriv itself --
# see terminus-valkey-container.bb. Forcing User=valkey at the
# orchestrator level would stop that chown from ever being able to run.
Volume=terminus-keyvalue.volume:${TERMINUS_VALKEY_DATA_DIR}

[Service]
Restart=on-failure
RestartSec=5
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-web.container << EOF
[Unit]
Description=Terminus web (podman, image-oci.bbclass build)
After=terminus-postgres.service terminus-valkey.service trmnl-oci-import@terminus-web.service
Requires=terminus-postgres.service terminus-valkey.service trmnl-oci-import@terminus-web.service

[Container]
Image=localhost/trmnl-terminus-web:latest
ContainerName=terminus-web
Network=host
Volume=terminus-uploads.volume:${TERMINUS_APP_DIR}/public/uploads
Volume=terminus-fonts.volume:${TERMINUS_APP_DIR}/public/fonts

[Service]
Restart=on-failure
RestartSec=5
EOF

    cat > ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/terminus-worker.container << EOF
[Unit]
Description=Terminus worker (podman, image-oci.bbclass build)
After=terminus-web.service trmnl-oci-import@terminus-worker.service
Requires=terminus-web.service trmnl-oci-import@terminus-worker.service

[Container]
Image=localhost/trmnl-terminus-worker:latest
ContainerName=terminus-worker
Network=host
Volume=terminus-uploads.volume:${TERMINUS_APP_DIR}/public/uploads
Volume=terminus-fonts.volume:${TERMINUS_APP_DIR}/public/fonts

[Service]
Restart=on-failure
RestartSec=5
EOF

    # With TRMNL_DEPLOY_METHOD = "docker", trmnl-compose@terminus.service
    # owns these containers. The Quadlet units stay installed -- `systemctl
    # start terminus-web` still works after stopping compose -- but
    # nothing pulls them in at boot, so the two paths can never run the
    # same container, bind the same port, or write the same volume
    # concurrently.
    if ${@'false' if d.getVar('TRMNL_DEPLOY_METHOD') == 'docker' else 'true'}; then
        for unit in terminus-postgres terminus-valkey terminus-web terminus-worker; do
            cat >> ${IMAGE_ROOTFS}${sysconfdir}/containers/systemd/${unit}.container << 'EOF'

[Install]
WantedBy=multi-user.target
EOF
        done
    fi
}

# valkey-server's own default working directory (where it snapshots
# RDB/AOF persistence files if not told otherwise) -- terminus-valkey-
# container.bb's OCI_IMAGE_CMD sets --dir explicitly to this same path,
# see that recipe. Named here, not hardcoded twice, so the two stay in
# sync if it ever changes.
TERMINUS_VALKEY_DATA_DIR = "/data"

# The compose file is cheap and useful to read on a device, so it is
# written whatever TRMNL_DEPLOY_METHOD says -- only the driver-service
# enablement below is conditional. Assigned here, not appended from an
# anonymous python block, so image.bbclass's own anonymous python (which
# snapshots this variable's expanded value into its 'vardeps' flag) always
# sees the full, correct function list -- see meta-trmnl TODO.md.
IMAGE_PREPROCESS_COMMAND += "terminus_container_compose_files "
IMAGE_PREPROCESS_COMMAND += "${@'terminus_container_compose_enable ' if d.getVar('TRMNL_DEPLOY_METHOD') == 'docker' else ''}"

terminus_container_compose_files () {
    install -d ${IMAGE_ROOTFS}${datadir}/trmnl/compose/terminus

    cat > ${IMAGE_ROOTFS}${datadir}/trmnl/compose/terminus/compose.yml << EOF
# Generated by terminus-container-image.bb -- do not edit on the target.
# Same topology as the Quadlet units in /etc/containers/systemd; run by
# trmnl-compose@terminus.service. See meta-trmnl/TODO.md.
#
# Deliberately close to upstream's own compose.yml
# (github.com/usetrmnl/terminus, root of repo) in shape -- restart
# policy, healthcheck-gated depends_on, init: true -- but NOT identical:
# - image: points at our own baked, already-imported localhost/trmnl-*
#   images (pull_policy: never, no registry involved), not
#   ghcr.io/usetrmnl/terminus:latest.
# - No ports:/service-name DNS/environment: block: this layer's images
#   are pre-configured via baked env files for Network=host / 127.0.0.1,
#   matching the Quadlet units, not upstream's bridge-network default.
# - No init-certificates service or certificates volume: this deployment
#   never talks to trmnl.com (og-Model-seed patch replaces that sync), so
#   there is nothing to fetch a CA bundle for.
# - No deploy.resources.limits: upstream's own numbers (1G+1G+2G+512M)
#   exceed what this project's qemu test images are configured with;
#   carrying them over unchanged would just make the test environment
#   OOM for no benefit.
name: trmnl-terminus

services:
  terminus-postgres:
    image: localhost/trmnl-terminus-postgres:latest
    pull_policy: never
    container_name: terminus-postgres
    network_mode: host
    restart: unless-stopped
    volumes:
      - trmnl-terminus-pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready --username terminus --dbname terminus --port 5432"]
      interval: 10s
      timeout: 5s
      retries: 3

  terminus-valkey:
    image: localhost/trmnl-terminus-valkey:latest
    pull_policy: never
    container_name: terminus-valkey
    network_mode: host
    # No user: here: the entrypoint starts as root, chowns the mounted
    # volume, then drops to valkey via setpriv itself -- see
    # terminus-valkey-container.bb. This is the same fix as the Quadlet
    # unit's now-removed User=valkey, needed because compose's own volume
    # creation left /data root-owned (confirmed live: valkey failed with
    # "Permission denied" opening its RDB temp file).
    restart: unless-stopped
    volumes:
      - trmnl-terminus-keyvalue:${TERMINUS_VALKEY_DATA_DIR}
    healthcheck:
      test: ["CMD-SHELL", "valkey-cli -p 6379 ping || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 2s

  terminus-web:
    init: true
    image: localhost/trmnl-terminus-web:latest
    pull_policy: never
    container_name: terminus-web
    network_mode: host
    restart: unless-stopped
    depends_on:
      terminus-postgres:
        condition: service_healthy
      terminus-valkey:
        condition: service_healthy
    volumes:
      - trmnl-terminus-uploads:${TERMINUS_APP_DIR}/public/uploads
      - trmnl-terminus-fonts:${TERMINUS_APP_DIR}/public/fonts
    healthcheck:
      # No curl in this image (confirmed live) -- wget is, and busybox
      # wget's own non-2xx-is-a-failure exit code is exactly what a
      # healthcheck needs.
      test: ["CMD-SHELL", "wget -q -O /dev/null --timeout=3 http://localhost:2300/up"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 45s

  terminus-worker:
    init: true
    image: localhost/trmnl-terminus-worker:latest
    pull_policy: never
    container_name: terminus-worker
    network_mode: host
    restart: unless-stopped
    depends_on:
      terminus-web:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "pgrep", "-f", "sidekiq"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 45s
    volumes:
      - trmnl-terminus-uploads:${TERMINUS_APP_DIR}/public/uploads
      - trmnl-terminus-fonts:${TERMINUS_APP_DIR}/public/fonts

volumes:
  trmnl-terminus-pgdata:
    name: trmnl-terminus-pgdata
  trmnl-terminus-keyvalue:
    name: trmnl-terminus-keyvalue
  trmnl-terminus-uploads:
    name: trmnl-terminus-uploads
  trmnl-terminus-fonts:
    name: trmnl-terminus-fonts
EOF
}

terminus_container_compose_enable () {
    # trmnl-compose@terminus.service (trmnl-compose-deploy package) needs
    # every image this app uses already imported, same as the Quadlet
    # units require individually -- derive the instance list from
    # TRMNL_OCI_APPS instead of hardcoding it, so an app added to or
    # dropped from that list is picked up automatically.
    install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/trmnl-compose@terminus.service.d

    insts=""
    for pair in ${TRMNL_OCI_APPS}; do
        insts="$insts trmnl-oci-import@${pair%%:*}.service"
    done
    insts="${insts# }"

    cat > ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/trmnl-compose@terminus.service.d/order.conf << EOF
[Unit]
After=$insts
Requires=$insts
EOF
}

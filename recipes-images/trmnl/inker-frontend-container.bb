SUMMARY = "Inker frontend (nginx + Vite bundle) as an image-oci.bbclass container"
DESCRIPTION = "Wraps inker-frontend (recipes-trmnl/inker/inker-frontend_0.6.0.bb) \
in meta-virtualization's container-base.bb shape. A small entrypoint wrapper \
runs ahead of nginx itself -- see inker_frontend_container_entrypoint below \
for why one is needed after all. Network=host is required at run time: \
inker-nginx.site's proxy_pass http://127.0.0.1:3002 assumes this container \
shares the inker-backend container's network namespace/loopback (see \
meta-trmnl/README.md's 'Design notes for Phase 2' section)."
HOMEPAGE = "https://github.com/usetrmnl/inker"
LICENSE = "AGPL-3.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/AGPL-3.0-only;md5=73f1eb20517c55bf9493b7dd6e480788"

require recipes-images/trmnl/trmnl-container-base.bb
TRMNL_CONTAINER_TITLE = "inker-frontend"

IMAGE_INSTALL:append = " nginx inker-frontend"

# nginx's own recipe creates /run/nginx and /var/log/nginx via tmpfiles.d
# (nginx-volatile.conf), applied by systemd-tmpfiles-setup.service on a
# normal boot -- this container has no init system (see the recipe header)
# so nothing ever runs that, and nginx fails immediately: "could not open
# error log file ... /var/log/nginx/error.log" then "mkdir()
# /run/nginx/client_body_temp failed (2: No such file or directory)".
# Confirmed live under the real Quadlet/podman run this recipe is for --
# not a guess from reading the tmpfiles.d rule alone.
IMAGE_PREPROCESS_COMMAND += "inker_frontend_container_entrypoint"
inker_frontend_container_entrypoint () {
    install -d ${IMAGE_ROOTFS}${libexecdir}
    cat > ${IMAGE_ROOTFS}${libexecdir}/inker-frontend-entrypoint.sh << 'EOF'
#!/bin/sh
set -e
mkdir -p /run/nginx /var/log/nginx
exec /usr/sbin/nginx -g 'daemon off;'
EOF
    chmod 0755 ${IMAGE_ROOTFS}${libexecdir}/inker-frontend-entrypoint.sh
}

OCI_IMAGE_ENTRYPOINT = "${libexecdir}/inker-frontend-entrypoint.sh"
OCI_IMAGE_PORTS = "80/tcp"

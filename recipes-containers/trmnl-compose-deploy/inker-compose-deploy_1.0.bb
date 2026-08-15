SUMMARY = "Run Inker's containers via podman-compose instead of Quadlet"
DESCRIPTION = "Ships the shared systemd oneshot template unit and wrapper \
script that drive `podman-compose up`/`down` against the compose.yml \
inker-container-image.bb writes into the rootfs (not packaged here -- see \
trmnl-compose-deploy.inc for why). The shared logic \
(trmnl-compose-deploy.inc) is generic across every trmnl app; this recipe \
is the thin per-image half -- see terminus-compose-deploy_1.0.bb for the \
other consumer."
HOMEPAGE = "https://github.com/koenkooi/meta-trmnl"

require trmnl-compose-deploy.inc
require recipes-containers/trmnl-oci-import/inker-apps.inc

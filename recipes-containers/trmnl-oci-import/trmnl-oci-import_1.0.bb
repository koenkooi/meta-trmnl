SUMMARY = "Import Terminus's OCI images into podman at boot"
DESCRIPTION = "Ships the shared systemd oneshot template unit and import \
script that load Terminus's four per-app OCI archives into podman at \
boot (the archives themselves are copied straight into the rootfs by \
terminus-container-image.bb via trmnl-oci-tars.inc, not packaged here -- \
see that file for why). The shared logic (trmnl-oci-import.inc) is \
generic across every trmnl app; this recipe is the thin per-image half \
-- see inker-oci-import_1.0.bb for the other consumer."
HOMEPAGE = "https://github.com/koenkooi/meta-trmnl"

require trmnl-oci-import.inc
require terminus-apps.inc

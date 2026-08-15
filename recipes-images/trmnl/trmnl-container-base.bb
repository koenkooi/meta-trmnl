SUMMARY = "Shared base for trmnl single-process OCI containers"
DESCRIPTION = "Common meta-virtualization container-base.bb boilerplate \
every trmnl *-container.bb recipe otherwise duplicated: OCI_IMAGE_CMD \
blanked (container-base.bb defaults it to /bin/sh, which OCI semantics \
would append to the entrypoint as an argument -- see meta-trmnl/README.md's \
'Phase 2: OCI images' section), a PATH-only OCI_IMAGE_ENV_VARS default for \
containers that need nothing else, and the OCI_IMAGE_LABELS vendor/title \
pair. LICENSE/LIC_FILES_CHKSUM stay in each app's own recipe -- they differ \
per app and aren't this file's business."

require recipes-extended/images/container-base.bb

OCI_IMAGE_CMD = ""

TRMNL_CONTAINER_TITLE ?= "${PN}"
OCI_IMAGE_LABELS = "org.opencontainers.image.vendor=Angstrom org.opencontainers.image.title=${TRMNL_CONTAINER_TITLE}"

OCI_IMAGE_ENV_VARS ?= "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

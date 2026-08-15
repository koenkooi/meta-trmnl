# Copies each app's baked OCI tar archive (hundreds of MB each) straight
# into IMAGE_ROOTFS, bypassing opkg/.ipk entirely -- required directly by
# each consuming *-container-image.bb, alongside its own <app>-apps.inc
# (which sets TRMNL_OCI_APPS; both are needed).
#
# Deliberately NOT a package: opkg is sized for ordinary software, not
# multi-hundred-MB binary blobs, and wrapping these tars in it silently
# masked real content staleness that cost a very long debugging session
# to even find (meta-trmnl TODO.md). A plain file copy into the rootfs,
# run every time do_rootfs runs, has no version/identity layer to go
# stale behind.

python () {
    apps = (d.getVar('TRMNL_OCI_APPS') or '').split()
    deps = ' '.join('%s:do_image_complete' % p.split(':')[1] for p in apps)
    if deps:
        d.appendVarFlag('do_rootfs', 'depends', ' ' + deps)
}

IMAGE_PREPROCESS_COMMAND += "trmnl_oci_tars_install"
trmnl_oci_tars_install () {
    install -d ${IMAGE_ROOTFS}${datadir}/trmnl
    for pair in ${TRMNL_OCI_APPS}; do
        instance="${pair%%:*}"
        recipe="${pair#*:}"
        install -m 0644 ${DEPLOY_DIR_IMAGE}/${recipe}-latest-oci.tar ${IMAGE_ROOTFS}${datadir}/trmnl/${instance}-latest-oci.tar
    done
}

SUMMARY = "ImageMagick PECL extension for PHP"
DESCRIPTION = "LaraPaper requires ext-imagick for its image-generation \
pipeline (composer.json: \"ext-imagick\": \"*\"). No existing recipe \
anywhere in this project's layers -- follows meta-webserver's xdebug.bb \
phpize/oe_runconf pattern, the proven in-layer PECL packaging shape."
HOMEPAGE = "https://pecl.php.net/package/imagick"
LICENSE = "PHP-3.01"
LIC_FILES_CHKSUM = "file://LICENSE;md5=dd34a70236f008af999de817b93a5e3a"

DEPENDS = "php imagemagick"

SRC_URI = "https://pecl.php.net/get/imagick-${PV}.tgz"
SRC_URI[sha256sum] = "3a3587c0a524c17d0dad9673a160b90cd776e836838474e173b549ed864352ee"

S = "${UNPACKDIR}/imagick-${PV}"

inherit autotools pkgconfig

EXTRA_OECONF += "--with-php-config=${STAGING_BINDIR_CROSS}/php-config"

# config.m4 declares this via PHP_ARG_WITH, not PHP_ARG_ENABLE --
# --enable-imagick was never a real flag here (silently unrecognized;
# only became fatal once do_configure started succeeding far enough to
# reach oe-core's unknown-configure-option QA check). The real switch
# is --with-imagick[=DIR], set below for its own reason.
#
# imagick 3.8.1's own imagemagick.m4 never uses pkg-config's Cflags (or
# CPPFLAGS/PKG_CONFIG_PATH at all) to find the header -- it does a raw
# shell `test -r "${prefix}/include/ImageMagick-N/..."`, where prefix
# comes from `pkg-config --variable=prefix MagickWand`. PKG_CONFIG_
# SYSROOT_DIR only rewrites -I/-L flags in Cflags/Libs output, not an
# arbitrary --variable query (this .pc file's own "prefix=/usr" line
# doesn't reference ${pc_sysrootdir}), so that check always tests the
# BUILD HOST's real /usr, never the target sysroot -- confirmed by
# reading imagemagick.m4 directly after CPPFLAGS-based fixes (both
# exported-by-hand and bitbake-level append) had zero effect. The
# macro's OTHER detection path (a real MagickWand-config binary,
# checked before it ever falls back to pkg-config) does honor whatever
# --prefix that binary reports, so feed it one: a tiny script with
# --prefix hardcoded to the real sysroot and --cflags/--libs still
# delegating to pkg-config (which sysroot-prefixes those two floats
# correctly -- only --variable=X is the gap).
EXTRA_OECONF += "--with-imagick=${WORKDIR}/fake-imagemagick-prefix"

do_configure() {
    cd ${S}
    ${STAGING_BINDIR_CROSS}/phpize
    cd ${B}
    mkdir -p ${WORKDIR}/fake-imagemagick-prefix/bin
    cat > ${WORKDIR}/fake-imagemagick-prefix/bin/MagickWand-config << EOF
#!/bin/sh
case "\$1" in
    --prefix) echo "${STAGING_DIR_TARGET}${prefix}" ;;
    --version) pkg-config --modversion MagickWand ;;
    --cflags) pkg-config --cflags MagickWand ;;
    --libs) pkg-config --libs MagickWand ;;
    *) echo "MagickWand-config: unsupported arg \$1" >&2; exit 1 ;;
esac
EOF
    chmod +x ${WORKDIR}/fake-imagemagick-prefix/bin/MagickWand-config
    oe_runconf
}

do_install() {
    oe_runmake install INSTALL_ROOT=${D}
    # `make install` also drops php_imagick_shared.h at a doubled,
    # bogus path -- ${D} followed by this build's own absolute
    # STAGING_DIR_TARGET (a header path baked in via php-config at
    # configure time, then INSTALL_ROOT=${D} prepended on top of an
    # already-absolute destination instead of a prefix-relative one).
    # Not a real packageable location either way -- a shared extension
    # doesn't need its own dev header installed at all. Remove from
    # ${TOPDIR} down, not just the header's own STAGING_DIR_TARGET
    # subtree, or the empty parent chain (${D}/build/tmp/work/...)
    # trips the same QA check on its own.
    rm -rf ${D}${TOPDIR}
}

FILES:${PN} += "${libdir}/php*/extensions/*/*.so"
FILES:${PN}-dbg += "${libdir}/php*/extensions/*/.debug"

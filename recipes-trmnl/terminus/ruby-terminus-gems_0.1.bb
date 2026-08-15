SUMMARY = "Vendored pure-Ruby gems for the Terminus TRMNL server"
DESCRIPTION = "Production-group gem closure of usetrmnl/terminus's \
Gemfile.lock, restricted to gems with no native (C) extension. Fetched \
directly from rubygems.org as plain .gem tarballs, pinned by the sha256 \
checksums Gemfile.lock already carries in its CHECKSUMS section -- no \
custom bitbake fetcher needed. \
Native-extension production gems (bcrypt, llhttp, nio4r, nokogiri, pg, \
puma, redcarpet, websocket-driver) are NOT part of this recipe: they come \
from meta-rubygems instead (its rubygems.bbclass cross-compiles by \
patching the target ruby's rbconfig.rb, no qemu-user needed). See \
terminus_0.68.0.bb's RDEPENDS and recipes-trmnl/terminus/rubygems-*.bb."
HOMEPAGE = "https://github.com/usetrmnl/terminus"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS = "ruby-native"

include terminus-gems-pure.inc

GEM_ABI_DIR = "4.0.0"
TERMINUS_GEM_HOME = "${libdir}/terminus/vendor/bundle/ruby/${GEM_ABI_DIR}"

do_compile() {
    rm -rf ${B}/gemhome
    mkdir -p ${B}/gemhome
    for gemfile in ${UNPACKDIR}/*.gem; do
        gem install --local --no-document --ignore-dependencies \
            --install-dir=${B}/gemhome "$gemfile"
    done
}

do_install() {
    install -d ${D}${TERMINUS_GEM_HOME}
    cp -r ${B}/gemhome/. ${D}${TERMINUS_GEM_HOME}/
    find ${D}${TERMINUS_GEM_HOME} -name gem_make.out -delete

    # gem install's bin/ stubs are a two-shebang trick: line 1 is a real
    # /bin/sh shebang that resolves $bindir/ruby (or PATH ruby) itself,
    # but each also embeds a second "#!<ruby-native path>" further down
    # purely so `ruby -x` can find where the real script starts -- that
    # path is never actually executed, just pattern-matched, so any
    # #!...ruby line is safe to blank out. None of these stubs are
    # invoked directly by this recipe's systemd units (bundle exec is),
    # but ship a portable shebang instead of a build-host TMPDIR path.
    if [ -d ${D}${TERMINUS_GEM_HOME}/bin ]; then
        sed -i 's|^#!.*ruby.*|#!/usr/bin/env ruby|' ${D}${TERMINUS_GEM_HOME}/bin/*
    fi
}

FILES:${PN} = "${TERMINUS_GEM_HOME}"

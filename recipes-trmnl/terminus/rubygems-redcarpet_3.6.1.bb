# SPDX-License-Identifier: MIT
SUMMARY = "RubyGem: redcarpet"
DESCRIPTION = "A fast, safe Markdown to (X)HTML parser, bundled C source (libsundown fork)"
HOMEPAGE = "https://github.com/vmg/redcarpet"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=5f1d7f6d5d6e23c84686886580395d2f"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "d444910e6aa55480c6bcdc0cdb057626e8a32c054c29e793fa642ba2f155f445"

GEM_NAME = "redcarpet"

require rubygems-common.inc

BBCLASSEXTEND = "native"

# SPDX-License-Identifier: MPL-2.0
SUMMARY = "RubyGem: llhttp"
DESCRIPTION = "Ruby bindings for llhttp, an HTTP request/response parser, bundled C source"
HOMEPAGE = "https://github.com/metabahn/llhttp"

LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d44fdeb607e2d2614db9464dbedd4094"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "3c3c59aafb1e1594ab2f45293478f697458f5a91eb0f0db4b27f61a064024982"

GEM_NAME = "llhttp"

require rubygems-common.inc

BBCLASSEXTEND = "native"

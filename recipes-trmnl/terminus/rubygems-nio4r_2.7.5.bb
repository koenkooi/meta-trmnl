# SPDX-License-Identifier: MIT
SUMMARY = "RubyGem: nio4r"
DESCRIPTION = "New IO for Ruby: a low-level selector API for monitoring IO objects, bundled libev by default"
HOMEPAGE = "https://github.com/socketry/nio4r"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://license.md;md5=ddebbff621c622e668ae20fb0e43e735"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "6c90168e48fb5f8e768419c93abb94ba2b892a1d0602cb06eef16d8b7df1dca1"

GEM_NAME = "nio4r"

require rubygems-common.inc

BBCLASSEXTEND = "native"

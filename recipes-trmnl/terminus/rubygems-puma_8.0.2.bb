# SPDX-License-Identifier: BSD-3-Clause
SUMMARY = "RubyGem: puma"
DESCRIPTION = "A Ruby/Rack web server, bundled C HTTP parser, no external lib required for plain HTTP"
HOMEPAGE = "https://puma.io"

LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=5e2fcd9abc7fc5134a5dfc6d31afe542"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "c8ed871dfbbe66448ea9ffd46692342d9804d4071522b52b5331b7b6e7b686fb"

GEM_NAME = "puma"

require rubygems-common.inc

BBCLASSEXTEND = "native"

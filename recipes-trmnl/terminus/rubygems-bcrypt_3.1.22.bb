# SPDX-License-Identifier: MIT
SUMMARY = "RubyGem: bcrypt"
DESCRIPTION = "OpenBSD's bcrypt() password hashing algorithm, self-contained C, no external lib"
HOMEPAGE = "https://github.com/bcrypt-ruby/bcrypt-ruby"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=64bd8a0d3896c920e7ff8ca686d2fe13"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "1f0072e88c2d705d94aff7f2c5cb02eb3f1ec4b8368671e19112527489f29032"

GEM_NAME = "bcrypt"

require rubygems-common.inc

BBCLASSEXTEND = "native"

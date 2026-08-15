# SPDX-License-Identifier: MIT
SUMMARY = "RubyGem: websocket-driver"
DESCRIPTION = "WebSocket protocol handler with pluggable I/O"
HOMEPAGE = "https://github.com/faye/websocket-driver-ruby"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE.md;md5=9b7b313780d45a859653127cf7439e17"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

DEPENDS:class-native += "\
    rubygems-base64-native \
    rubygems-websocket-extensions-native \
"

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "97c556b019bf3410b4961002ac501621e9322d3f8a7bc02161a09301cc4c4146"

GEM_NAME = "websocket-driver"

require rubygems-common.inc
inherit pkgconfig

RDEPENDS:${PN}:class-target += "\
    rubygems-base64 \
    rubygems-websocket-extensions \
"

# meta-rubygems (pinned dba6dfb) only carries 0.8.0; Gemfile.lock wants
# 0.8.2. Local bump, outranks meta-rubygems on PV.

BBCLASSEXTEND = "native"

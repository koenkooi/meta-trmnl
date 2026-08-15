# SPDX-License-Identifier: Ruby
SUMMARY = "RubyGem: json"
DESCRIPTION = "This is a JSON implementation as a Ruby extension in C."
HOMEPAGE = "http://flori.github.io/json/"

LICENSE = "Ruby"
LIC_FILES_CHKSUM = "file://COPYING;md5=8a960b08d972f43f91ae84a6f00dcbfb"

SRC_URI[sha256sum] = "1f1d3b7cf2b3ba1a69beca0bb6db13d5438b80bff3cd54cdaaa620b9b07c1c6a"

GEM_NAME = "json"

require rubygems-common.inc
inherit pkgconfig

# Outranks both meta-rubygems' 2.5.1 and the empty stdgems 1.0 meta-package
# on PV, which is what actually closes the rubygems-json shadowing bug --
# ruby's own default json is 2.18.0, still below the lock's 2.21.2.

BBCLASSEXTEND = "native"

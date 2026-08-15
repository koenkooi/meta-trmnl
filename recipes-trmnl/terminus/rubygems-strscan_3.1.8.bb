# SPDX-License-Identifier: Ruby
SUMMARY = "RubyGem: strscan"
DESCRIPTION = "Provides lexical scanning operations on a String"
HOMEPAGE = "https://github.com/ruby/strscan"

LICENSE = "Ruby | BSD-2-Clause"
LIC_FILES_CHKSUM = "file://COPYING;md5=1be94bf7352dbaeddf9d7d16184103c3"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "aae2db611a225559f21ffbb71765c9a4e60fd262534a9ea84f4f11c7f32f679e"

GEM_NAME = "strscan"

require rubygems-common.inc

# Ruby 4.0.6 ships strscan 3.1.6 as a default gem; Gemfile.lock wants 3.1.8.

BBCLASSEXTEND = "native"

# SPDX-License-Identifier: MIT
SUMMARY = "RubyGem: prism"
DESCRIPTION = "Prism Ruby parser, used by irb/rdoc and Bundler's own tooling"
HOMEPAGE = "https://github.com/ruby/prism"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.md;md5=f303cb5ddf301687daade96b06b7a139"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "7b530c6a9f92c24300014919c9dcbc055bf4cdf51ec30aed099b06cd6674ef85"

GEM_NAME = "prism"

require rubygems-common.inc

# Ruby 4.0.6 ships prism 1.8.1 as a default gem; Gemfile.lock wants 1.9.0.

BBCLASSEXTEND = "native"

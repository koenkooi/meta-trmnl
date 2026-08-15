# SPDX-License-Identifier: Ruby
SUMMARY = "RubyGem: erb"
DESCRIPTION = "An easy to use but powerful templating system for Ruby"
HOMEPAGE = "https://github.com/ruby/erb"

LICENSE = "Ruby | BSD-2-Clause"
LIC_FILES_CHKSUM = "file://COPYING;md5=5b8c87559868796979806100db3f3805"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "c5ca6dc25b0ef974a44dc8f59fe847577122483b1968a38dec305c60bf91ee92"

GEM_NAME = "erb"

require rubygems-common.inc

# Ruby's own ruby package already owns ${bindir}/erb; this gem's bin stub
# collides with it. Nothing on the target invokes erb(1) -- every unit runs
# via "bundle exec" -- so drop the stub rather than reach for ALTERNATIVES.
do_install:append () {
    rm -f ${D}${bindir}/erb
    rmdir --ignore-fail-on-non-empty ${D}${bindir}
}

# Ruby 4.0.6 ships erb 6.0.1.1 as a default gem; Gemfile.lock wants 6.0.7.

BBCLASSEXTEND = "native"

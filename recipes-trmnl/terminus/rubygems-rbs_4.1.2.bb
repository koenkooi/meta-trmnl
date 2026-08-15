# SPDX-License-Identifier: BSD-2-Clause
SUMMARY = "RubyGem: rbs"
DESCRIPTION = "Type signature language and tools for Ruby"
HOMEPAGE = "https://github.com/ruby/rbs"

LICENSE = "BSD-2-Clause | Ruby"
LIC_FILES_CHKSUM = "file://COPYING;md5=f90b6181f8f7d0a82707383d7475c432"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "050eb1d8b508f1233bed929c0f2c7052302f7adf295230d9cb314e9024078f48"

GEM_NAME = "rbs"

require rubygems-common.inc

# Collides with ruby's own bindir the same way erb does (both install a
# bin/ stub nothing on the target invokes -- bundle exec is what runs).
do_install:append () {
    rm -f ${D}${bindir}/rbs
    rmdir --ignore-fail-on-non-empty ${D}${bindir}
}

# Ruby 4.0.6 ships rbs 3.10.0 bundled; Gemfile.lock wants 4.1.2. Largest of
# the six default-gem bumps: a real C extension plus a big pure-Ruby tree.

BBCLASSEXTEND = "native"

# SPDX-License-Identifier: Ruby
SUMMARY = "RubyGem: bigdecimal"
DESCRIPTION = "Arbitrary-precision decimal floating-point number library"
HOMEPAGE = "https://github.com/ruby/bigdecimal"

LICENSE = "Ruby | BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=837b32593517ae48b9c3b5c87a5d288c"

EXTRA_DEPENDS:append = " "
EXTRA_RDEPENDS:append = " "

GEM_INSTALL_FLAGS:append = " "

SRC_URI[sha256sum] = "53d217666027eab4280346fba98e7d5b66baaae1b9c3c1c0ffe89d48188a3fbd"

GEM_NAME = "bigdecimal"

require rubygems-common.inc

# Ruby 4.0.6 ships bigdecimal 4.0.1 bundled; Gemfile.lock wants 4.1.2.
# Highest-PV wins over the empty recipes-stdgems/rubygems-bigdecimal_1.0.bb.
# Candidate for retirement once meta-rubygems moves past its wrynose pin
# (dba6dfb).

BBCLASSEXTEND = "native"

# SPDX-License-Identifier: MIT
SUMMARY = "RubyGem: nokogiri"
DESCRIPTION = "Nokogiri (鋸) makes it easy and painless to work with XML and HTML from Ruby"
HOMEPAGE = "https://nokogiri.org"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE-DEPENDENCIES.md;md5=7e7a84c854a5e89a25905bfc881b3b0b"

EXTRA_DEPENDS:append = " \
    libxml2 \
    libxslt \
"
EXTRA_RDEPENDS:append = " "

DEPENDS:class-native += "\
    rubygems-mini-portile2-native \
    rubygems-racc-native \
"
DEPENDS:class-target += "\
    rubygems-mini-portile2 \
"

GEM_INSTALL_FLAGS:append = " \
    --use-system-libraries \
"

SRC_URI[sha256sum] = "50c951611c92bca05c51411aef45f1cbc50f2821c4802758c5c6d34696533ab5"

GEM_NAME = "nokogiri"

require rubygems-common.inc
inherit pkgconfig

RDEPENDS:${PN}:class-target += "\
    rubygems-mini-portile2 \
    rubygems-racc \
"

INSANE_SKIP:${PN}-dev += "staticdev"

# meta-rubygems (pinned dba6dfb) carries 1.19.3; Gemfile.lock wants 1.19.4.
# This is the plain-ruby-platform source gem (sha256 above), not the
# precompiled aarch64-linux-gnu one the lock's own Gemfile.lock stanzas name
# -- do_install below rewrites the copied lock to accept it.

BBCLASSEXTEND = "native"

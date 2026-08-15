# SPDX-License-Identifier: BSD-2-Clause
SUMMARY = "RubyGem: pg"
DESCRIPTION = "Ruby interface to PostgreSQL, links against libpq"
HOMEPAGE = "https://github.com/ged/ruby-pg"

LICENSE = "BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=837b32593517ae48b9c3b5c87a5d288c"

EXTRA_DEPENDS:append = " \
    postgresql \
"
EXTRA_RDEPENDS:append = " \
    libpq \
"

# pg_config is a target binary and can't run during the native-ruby-driven
# install step -- point extconf at the staged libpq directly instead of
# letting it search PATH for pg_config.
GEM_INSTALL_FLAGS:append = " \
    -- \
    --with-opt-dir=${RECIPE_SYSROOT}${prefix} \
"

SRC_URI[sha256sum] = "1388d0563e13d2758c1089e35e973a3249e955c659592d10e5b77c468f628a99"

GEM_NAME = "pg"

require rubygems-common.inc
inherit pkgconfig

BBCLASSEXTEND = "native"

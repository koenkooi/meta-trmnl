# Shared by every native-extension rubygems-*.bb recipe here.
inherit rubygems
inherit rubygentest

# Every native-extension gem's compiled .so links with an RPATH into
# ${WORKDIR}/recipe-sysroot-native -- confirmed live via a real build's
# do_package_qa failure ("bad RPATH ... recipe-sysroot-native/usr/lib").
# rubygems.bbclass's own gem install runs ruby's mkmf-generated
# extconf.rb, which pulls its DLDFLAGS from the NATIVE ruby's own
# RbConfig::CONFIG (needed to run extconf.rb at all), and that native
# ruby's own build already bakes an rpath to its own native libdir into
# every extension it configures -- not something a per-recipe flag can
# turn off from here, and not a case for INSANE_SKIP.
#
# Tried and confirmed NOT sufficient: oe-core's chrpath.bbclass (which
# PREPROCESS_RELOCATE_DIRS feeds) only ever runs its rpath_replace() from
# perform_packagecopy() for nativesdk/cross-canadian recipes -- see
# package.bbclass. A plain target recipe like every gem here never calls
# it at all, PREPROCESS_RELOCATE_DIRS or not; verified live, the QA error
# was byte-for-byte identical with it set.
#
# The path baked into the RPATH cannot exist on the target device --
# WORKDIR is build-host-only -- so there is no runtime search path this
# strip could ever break; every real runtime dependency of these gems
# (libpq, libxml2, ...) is declared through normal RDEPENDS and resolved
# via the target's standard library search path instead.
DEPENDS:append = " chrpath-native"
do_install:append () {
    find ${D}${GEM_DIR} -name '*.so*' -type f -print0 | xargs -0 --no-run-if-empty chrpath -d
}

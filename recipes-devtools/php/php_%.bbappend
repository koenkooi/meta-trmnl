# LaraPaper's composer.json requires ext-zip; oe-core's php recipe ships
# PACKAGECONFIG[zip] but leaves it off by default.
PACKAGECONFIG:append = " zip"

# meta-oe defaults PACKAGECONFIG to include mysql, which DEPENDS on mariadb,
# which DEPENDS on boost -- so a sqlite-only Laravel image dragged both into
# its rootfs (bitbake -g larapaper-image: larapaper-image.do_rootfs ->
# boost.do_package_write_ipk, via php.do_prepare_recipe_sysroot ->
# mariadb.do_populate_sysroot). Nothing here needs it: composer.json requires
# no ext-pdo_mysql and config/database.php guards its mysql driver behind
# extension_loaded('pdo_mysql').
PACKAGECONFIG:remove = "mysql"

# php-native runs composer.phar for larapaper-vendor's do_compile.
# oe-core's php-native build is deliberately minimal (PACKAGECONFIG:class-
# native = "", EXTRA_OECONF:class-native has --without-iconv, no
# --enable-mbstring, no --with-openssl) -- Composer's Factory bootstrap
# hard-requires both mbstring/iconv and openssl even for a fully offline
# (artifact-repository) install. mbstring needs no extra DEPENDS (PHP's
# own bundled tables, not oniguruma); openssl does.
EXTRA_OECONF:append:class-native = " --enable-mbstring --with-openssl"
DEPENDS:append:class-native = " openssl-native"

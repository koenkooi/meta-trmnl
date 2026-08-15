SUMMARY = "LaraPaper, a self-hosted BYOS server for TRMNL e-ink displays"
DESCRIPTION = "Laravel 13 app, served via php artisan's built-in dev \
server for this Phase 1 test image (no nginx/php-fpm split yet). \
Composer vendor tree comes from the separate larapaper-vendor package. \
KNOWN INCOMPLETE: the front-end assets are not built, so EVERY HTML \
route 500s -- not just an unstyled page. Confirmed from a booted guest: \
\"Vite manifest not found at: .../public/build/manifest.json (View: \
resources/views/partials/head.blade.php)\", and head.blade.php is in \
every layout. Upstream .gitignore's /public/build means no manifest \
ships and there is nothing to install; building one needs the npm \
closure (169 prod packages), nodejs-native, and vendor/ present at the \
same time (resources/css/app.css @imports vendor/livewire/flux/dist/ \
flux.css). No target Chromium/Puppeteer either. Unlike Terminus, the \
device API does NOT need Chromium here: /api/setup and /api/display \
resolve their image through \
ImageGenerationService::getDeviceSpecificDefaultImage() first, which is \
satisfied by upstream's own checked-in storage/app/public/images/* \
(pre-rendered per DeviceModel) before ever falling back to the \
Browsershot render path. This recipe proves the PHP/Laravel app \
installs, boots its web process, migrates against sqlite, and answers \
a real device pairing cycle. No boot-test seeder ships in the image --\
boot-validate.py's own --check probes provision the one admin-flag row \
auto-provisioning needs, via php artisan tinker against the running \
guest, so APP_ENV stays production and nothing test-only reaches the \
rootfs."
HOMEPAGE = "https://github.com/usetrmnl/larapaper"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.md;md5=9e1d095412d3a923e7c1e2e670d3d2d3"

SRC_URI = "git://github.com/usetrmnl/larapaper.git;protocol=https;branch=main \
           file://larapaper-web.service \
           file://larapaper.env \
           file://imagick.ini \
           "
SRCREV = "e8e7ae5509a8d26e02c2c953f47555494fa8f4e5"
PV = "0.1+git"

inherit systemd useradd

LARAPAPER_APP_DIR = "${libdir}/larapaper/app"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM:${PN} = "--system --home-dir ${LARAPAPER_APP_DIR} --shell /bin/false larapaper"

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = "larapaper-web.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${LARAPAPER_APP_DIR}
    cp -r ${S}/. ${D}${LARAPAPER_APP_DIR}/
    rm -rf ${D}${LARAPAPER_APP_DIR}/.git

    # vendor/ is NOT installed here -- it's the separate larapaper-vendor
    # package, which installs straight into ${LARAPAPER_APP_DIR}/vendor
    # itself (both recipes agree on the literal path). A symlink to a
    # physically separate directory does NOT work: Composer's generated
    # autoload_static.php computes $baseDir as dirname(__DIR__) from
    # vendor/composer's own REAL (symlink-resolved) location, so vendor/
    # must be a genuine child of the app root or App\* classes 404.
    rm -rf ${D}${LARAPAPER_APP_DIR}/vendor

    # App tree ships read-only. storage/app (default-screen images the
    # app ships pre-rendered under storage/app/public/images) stays part
    # of it; the paths Laravel writes at runtime are symlinked out to
    # StateDirectory=larapaper (/var/lib/larapaper) instead. The sqlite
    # DB lives there too via DB_DATABASE in larapaper.env.
    rm -rf ${D}${LARAPAPER_APP_DIR}/storage/framework \
           ${D}${LARAPAPER_APP_DIR}/storage/logs \
           ${D}${LARAPAPER_APP_DIR}/bootstrap/cache
    ln -s /var/lib/larapaper/framework ${D}${LARAPAPER_APP_DIR}/storage/framework
    ln -s /var/lib/larapaper/logs ${D}${LARAPAPER_APP_DIR}/storage/logs
    ln -s /var/lib/larapaper/bootstrap-cache ${D}${LARAPAPER_APP_DIR}/bootstrap/cache

    # Equivalent of upstream's own `php artisan storage:link`: public/
    # is served directly by artisan serve, so image_url only resolves
    # for a real HTTP client with this in place.
    ln -s ../storage/app/public ${D}${LARAPAPER_APP_DIR}/public/storage

    install -d ${D}${sysconfdir}/larapaper/php.d
    install -m 0644 ${UNPACKDIR}/larapaper.env ${D}${sysconfdir}/larapaper/larapaper.env
    install -m 0644 ${UNPACKDIR}/imagick.ini ${D}${sysconfdir}/larapaper/php.d/imagick.ini

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/larapaper-web.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = "${LARAPAPER_APP_DIR} ${sysconfdir}/larapaper ${systemd_system_unitdir}"

RDEPENDS:${PN} = "php-cli php-imagick larapaper-vendor"

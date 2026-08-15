SUMMARY = "Headless Chromium wiring for Terminus's Ferrum::Browser calls"
DESCRIPTION = "Terminus's Ferrum::Browser.new call (app/aspects/screens/temp_pather.rb, \
reached from every device-facing screen render) spawns a target-arch \
headless Chromium at runtime. This package RDEPENDS on the real binary \
-- meta-browser's chromium-ozone-wayland, see kas/angstrom.yml -- and \
drops a systemd Environment= override onto both terminus service units \
so ferrum's browser_path resolves explicitly (BROWSER_PATH) instead of \
relying on its own PATH search. It does NOT set no-sandbox/disable-gpu: \
those are Ferrum::Browser.new's browser_options: hash, app-side Ruby \
that a systemd Environment= cannot reach."
HOMEPAGE = "https://github.com/usetrmnl/terminus"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://10-chromium.conf"

S = "${UNPACKDIR}"

RDEPENDS:${PN} = "chromium-ozone-wayland"

do_install() {
    install -d ${D}${sysconfdir}/systemd/system/terminus-web.service.d
    install -d ${D}${sysconfdir}/systemd/system/terminus-worker.service.d
    install -m 0644 ${UNPACKDIR}/10-chromium.conf ${D}${sysconfdir}/systemd/system/terminus-web.service.d/
    install -m 0644 ${UNPACKDIR}/10-chromium.conf ${D}${sysconfdir}/systemd/system/terminus-worker.service.d/
}

FILES:${PN} = "${sysconfdir}/systemd/system"

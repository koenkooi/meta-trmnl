# Terminus needs postgresql running out of the box on the trmnl test
# image; oe-core's default is intentionally opt-in (disable), flip it
# here rather than in the shared recipe.
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

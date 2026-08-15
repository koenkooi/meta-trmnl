# Baseline console-image content every image recipe in this directory
# requires: a working systemd network config, package management, and the
# usual debug/admin tools. Folded in here rather than requiring
# meta-angstrom's recipes-images/angstrom/console-base-image.bb, so this
# layer's own images do not depend on meta-angstrom.
#
# Two things NOT carried over, both confirmed live via real "Nothing
# RPROVIDES" build failures against this layer's own composed layers
# (nothing meta-angstrom-specific in the mix):
# - ${DISTROFEEDCONFIGS}: an Angstrom distro variable (a package name for
#   its own feed-config package) with no definition anywhere under
#   distro: nodistro -- bitbake leaves a genuinely undeclared variable
#   reference un-expanded rather than blank, so the literal string
#   "${DISTROFEEDCONFIGS}" ended up in RDEPENDS and broke every image.
# - systemd-zram-generator and fstrim-timer: both real recipes, but only
#   in meta-angstrom's own recipes-core -- board/power-tuning packages
#   with no upstream equivalent in any layer this composition carries,
#   out of place in a generic console baseline anyway.

inherit core-image

DISTRO_UPDATE_ALTERNATIVES ??= ""
ROOTFS_PKGMANAGE_PKGS ?= '${@oe.utils.conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "${ROOTFS_PKGMANAGE} ${DISTRO_UPDATE_ALTERNATIVES}", d)}'

# Debug features, disable if wanted
# allow-empty-password (PermitEmptyPasswords yes) is intentionally left out: with
# UsePAM it turns every ssh 'none' auth probe into an empty-password PAM attempt,
# adding pam_unix's 2s fail delay to each login. This also means root's blanked
# password from empty-root-password can no longer be used over ssh -- only console
# login and pubkey auth -- which is an acceptable trade for a fast default.
IMAGE_FEATURES += "empty-root-password"

# Debug tools, leave in
IMAGE_FEATURES += "package-management nfs-client ssh-server-openssh"

CORE_IMAGE_EXTRA_INSTALL += " \
	${ROOTFS_PKGMANAGE_PKGS} \
	systemd-networkd systemd-analyze udev-hwdb \
	tzdata cronie \
        bash \
        avahi-daemon avahi-utils \
	net-tools lldpd iproute2-tc ethtool \
        wget curl \
        vim \
        git \
	kernel-modules \
        util-linux-fstrim util-linux-blkdiscard \
        e2fsprogs-resize2fs \
        htop \
        usb-modeswitch \
        iwd \
	libgpiod \
	bc \
"

IMAGE_PREPROCESS_COMMAND += "trmnl_console_base_systemd_network ; "

trmnl_console_base_systemd_network () {
	install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/network
	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/10-en.network
[Match]
Name=en*

[Network]
DHCP=yes
LLDP=yes
EmitLLDP=yes

[DHCPv4]
UseTimezone=yes
RouteMetric=10

[IPv6AcceptRA]
RouteMetric=10

[Route]
Metric=10
EOF

	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/11-eth.network
[Match]
Name=eth*

[Network]
DHCP=yes
LLDP=yes
EmitLLDP=yes

[DHCPv4]
UseTimezone=yes
RouteMetric=10

[IPv6AcceptRA]
RouteMetric=10

[Route]
Metric=10
EOF

	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/12-wlan.network
[Match]
Name=wlan*

[Network]
DHCP=yes
LLDP=yes
EmitLLDP=yes

[DHCPv4]
UseTimezone=yes
RouteMetric=100

[IPv6AcceptRA]
RouteMetric=100

[Route]
Metric=100
EOF
}

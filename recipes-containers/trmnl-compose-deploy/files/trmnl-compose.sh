#!/bin/sh
# Drives one app's generated compose file. "$1" is the app name (terminus,
# inker), "$2" is up|down. The compose file is written into the rootfs by
# that app's *-container-image.bb, not packaged -- see trmnl-compose-deploy.inc.
set -e

APP="$1"
ACTION="$2"
[ -n "${APP}" ] && [ -n "${ACTION}" ] || { echo "usage: $0 <app> up|down" >&2; exit 1; }

DIR="/usr/share/trmnl/compose/${APP}"
FILE="${DIR}/compose.yml"
PROJECT="trmnl-${APP}"
[ -f "${FILE}" ] || { echo "$0: no ${FILE}" >&2; exit 1; }

# "docker compose" (space, not the standalone docker-compose binary) works
# under both engines without picking a binary by hand: podman's own package
# installs a `docker` command (PODMAN_FEATURES defaults to "docker") plus a
# builtin `compose` subcommand that re-execs whatever compose
# implementation it finds on PATH with the same arguments unchanged; a real
# docker-moby install resolves the same subcommand through
# docker-compose's own CLI-plugin install path. Whichever engine
# TRMNL_DEPLOY_METHOD ends up meaning, this line does not need to change.
set -- docker compose

# podman-compose puts every service into a shared pod by default, and a pod
# owns the network namespace -- incompatible with the per-service
# network_mode: host every service here needs. --in-pod=false is a
# podman-compose-specific flag: a real docker compose plugin does not know
# it, so only add it when podman-compose is what will actually run.
if command -v podman-compose >/dev/null 2>&1; then
    set -- "$@" --in-pod=false
fi

case "${ACTION}" in
    # -p and the compose file's own top-level name: are the same string on
    # purpose, so it does not matter which one the implementation honours.
    up)   exec "$@" -f "${FILE}" -p "${PROJECT}" up -d ;;
    # never -v: that would delete the named volumes this whole design exists
    # to share with the Quadlet units.
    down) exec "$@" -f "${FILE}" -p "${PROJECT}" down ;;
    *)    echo "$0: unknown action ${ACTION}" >&2; exit 1 ;;
esac

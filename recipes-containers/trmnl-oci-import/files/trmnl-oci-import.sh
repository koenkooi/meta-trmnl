#!/bin/sh
# Imports one app's OCI image archive, baked into the rootfs at build time
# (never fetched over the network), into podman at boot. "$1" is the
# app/instance name; the archive lives at
# /usr/share/trmnl/${APP}-latest-oci.tar, installed there by this package's
# own do_install.
#
# Idempotency is content-addressed: a sha256 of the ARCHIVE (not a PV, not
# just "does the tag exist") is stamped at
# /var/lib/containers/trmnl-oci-import/${APP}.sha256 once the import that
# produced it has fully succeeded. That directory is also this script's own
# persistent log location -- see below for why not /var/log.
#
# The three env vars below default to the real device paths and exist only
# so a host-side dry run can point them at a scratch tree without touching
# a real filesystem; production runs (systemd) never set them.
APP="$1"
if [ -z "${APP}" ]; then
    echo "usage: $0 <app-name>" >&2
    exit 1
fi

STAMP_DIR="${TRMNL_OCI_IMPORT_STATE_DIR:-/var/lib/containers/trmnl-oci-import}"
ARCHIVE_DIR="${TRMNL_OCI_IMPORT_ARCHIVE_DIR:-/usr/share/trmnl}"
LOCKFILE="${TRMNL_OCI_IMPORT_LOCKFILE:-/run/trmnl-oci-import.lock}"

ARCHIVE="${ARCHIVE_DIR}/${APP}-latest-oci.tar"
STAMP="${STAMP_DIR}/${APP}.sha256"
TAG="localhost/trmnl-${APP}:latest"

# /var/log is a symlink to tmpfs on this image (base-files' volatile-log
# perms table), so anything logged there dies on the very reboot that ends
# a stall. /var/lib/containers shares the graphroot's lifetime and is
# genuinely persistent (volatile-binds' unit is rw-gated and never fires
# here) -- log there instead. One rotation: this boot's log plus the last.
mkdir -p "${STAMP_DIR}"
[ -s "${STAMP_DIR}/${APP}.log" ] && mv -f "${STAMP_DIR}/${APP}.log" "${STAMP_DIR}/${APP}.log.1"
exec >>"${STAMP_DIR}/${APP}.log" 2>&1

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

log "starting import for ${APP}"

[ -f "${ARCHIVE}" ] || { log "archive not found: ${ARCHIVE}"; exit 1; }

# openssl, not sha256sum -- these images have no coreutils and busybox's
# defconfig has CONFIG_SHA256SUM unset. openssl-bin is pinned explicitly in
# RDEPENDS rather than relied on as a transitive dependency. $NF handles
# both "SHA2-256(path)= <hex>" and "-r"'s "<hex> *path" output shapes.
archive_sha256() { openssl dgst -sha256 "$1" | awk '{print $NF}'; }

# Pure read of the rootfs, contends with nothing -- compute before the lock
# so both the fast path and the real import path can use it.
WANT=$(archive_sha256 "${ARCHIVE}")
log "archive=${ARCHIVE} size=$(wc -c <"${ARCHIVE}") sha256=${WANT}"

# The stamp alone is not enough: a wiped graphroot plus a surviving stamp
# must NOT look like "done" (TODO.md). Only stamp-matches-AND-image-present
# counts as satisfied. Used both before and after the lock -- another
# instance may finish while this one is waiting to acquire it.
guard_ok() {
    [ -r "${STAMP}" ] && [ "$(cat "${STAMP}")" = "${WANT}" ] && podman image exists "${TAG}"
}

if guard_ok; then
    log "stamp matches and ${TAG} present, nothing to do"
    exit 0
fi

set -e

# TMPDIR is instance-scoped by the unit (Environment=TMPDIR=.../%i); only
# ever remove it if it still looks like that scoped path, so a stray
# inherited TMPDIR (e.g. a manual/test invocation) can never be nuked.
# HB_PID is the heartbeat's background PID, killed here if still running.
cleanup() {
    [ -n "${HB_PID:-}" ] && kill "${HB_PID}" 2>/dev/null
    case "${TMPDIR:-}" in
        /var/lib/containers/tmp/*) rm -rf "${TMPDIR}" ;;
    esac
}
trap cleanup EXIT INT TERM HUP

# util-linux-flock is genuinely in both shipped manifests and busybox's own
# CONFIG_FLOCK=y is on -- either provides a kernel-released lock, which
# structurally rules out a stale-lock-forever hang the old mkdir lock could
# not. Bounded wait: a timeout is a logged failure, not a silent spin.
exec 9>"${LOCKFILE}"
flock -w 1800 9 || { log "lock wait timed out after 1800s"; exit 1; }

if guard_ok; then
    log "stamp matches and ${TAG} present (settled while we waited for the lock), nothing to do"
    exit 0
fi

# Capture before anything changes, so the superseded image can be removed
# precisely by ID once the new one is in place.
OLD_ID=$(podman image inspect --format '{{.Id}}' "${TAG}" 2>/dev/null) || OLD_ID=""

# Runs "$@" in the background and logs progress every 15s: elapsed time,
# the child's /proc state (D/S/gone discriminates "genuinely working" from
# "blocked" from "stale lock" without needing a live boot to catch it),
# free memory and rootfs space. Sets HB_OUT to the child's captured stdout.
with_heartbeat() {
    HB_OUT=$(mktemp "${TMPDIR:-/tmp}/trmnl-oci-import.XXXXXX")
    "$@" >"${HB_OUT}" &
    child=$!
    # Poll liveness every 1s (bounds kill/wait latency below to ~1s) but
    # only log every 15th tick -- keeps the log cadence from the plan
    # without making a stall's own kill signal wait out a long sleep.
    ( start=$(date +%s); n=0
      while kill -0 "${child}" 2>/dev/null; do
          sleep 1
          n=$((n + 1))
          [ "${n}" -lt 15 ] && continue
          n=0
          state=$(awk '{print $3}' "/proc/${child}/stat" 2>/dev/null) || state=""
          mem=$(awk '/MemAvailable/{print $2}' /proc/meminfo 2>/dev/null) || mem=""
          disk=$(df -k /var/lib/containers 2>/dev/null | tail -n1) || disk=""
          log "heartbeat: elapsed=$(( $(date +%s) - start ))s pid=${child} state=${state:-?} memavail=${mem:-?}kB df=${disk:-?}"
      done
    ) &
    HB_PID=$!
    if wait "${child}"; then rc=0; else rc=$?; fi
    kill "${HB_PID}" 2>/dev/null; wait "${HB_PID}" 2>/dev/null
    HB_PID=""
    return "${rc}"
}

# `podman pull` against a local oci-archive: URI both loads the image AND
# resolves its ID in one step -- proven live in this project's own
# boot-validate.py. rc is captured explicitly (never masked) so a genuine
# failure is logged instead of silently redoing ~1.2 GiB via `load`.
if with_heartbeat podman pull "oci-archive:${ARCHIVE}"; then rc=0; else rc=$?; fi
IMAGE_ID=$(cat "${HB_OUT}"); rm -f "${HB_OUT}"
log "podman pull rc=${rc} image_id=${IMAGE_ID:-<empty>}"

if [ "${rc}" -ne 0 ]; then
    log "podman pull failed rc=${rc}, falling back to podman load"
fi

if [ "${rc}" -ne 0 ] || [ -z "${IMAGE_ID}" ]; then
    # Unverified codepath: podman's `load` output format is not confirmed
    # to match docker's "Loaded image ID: sha256:..." line, so parse
    # generically (a bare hex ID, or a "Loaded image: <ref>" line resolved
    # back through `podman images`) instead of matching a specific string.
    if with_heartbeat podman load -i "${ARCHIVE}"; then load_rc=0; else load_rc=$?; fi
    LOAD_OUTPUT=$(cat "${HB_OUT}"); rm -f "${HB_OUT}"
    log "${LOAD_OUTPUT}"
    [ "${load_rc}" -ne 0 ] && { log "podman load also failed rc=${load_rc}"; exit 1; }
    IMAGE_ID=$(echo "${LOAD_OUTPUT}" | grep -oE "[a-f0-9]{12,64}" | tail -n1)

    if [ -z "${IMAGE_ID}" ]; then
        LOADED_REF=$(echo "${LOAD_OUTPUT}" | sed -n "s/^Loaded image: //p")
        IMAGE_ID=$(podman images --format "{{.ID}} {{.Repository}}:{{.Tag}}" 2>/dev/null \
            | awk -v ref="${LOADED_REF}" '$2 == ref { print $1; exit }')
    fi

    if [ -z "${IMAGE_ID}" ]; then
        IMAGE_ID=$(podman images --format "{{.ID}} {{.Repository}}:{{.Tag}}" 2>/dev/null \
            | awk '$2 == "latest:latest" { print $1; exit }')
    fi
fi

if [ -z "${IMAGE_ID}" ]; then
    log "could not resolve the image ID loaded from ${ARCHIVE}"
    exit 1
fi

# Tag the immutable name first, then move :latest onto it. That order means
# a failure mid-sequence never leaves :latest naming nothing -- the image is
# already reachable by its version tag before :latest is detached from the
# old one. Version is derived from the archive hash, not a recipe PV: this
# layer has already been burned twice by version-keyed identity over
# content that can change without the version (image-oci.bbclass's PKGV
# cache, the old opkg-wrapped tars) -- a hash can't go stale by construction.
VERSION_TAG="localhost/trmnl-${APP}:sha256-$(echo "${WANT}" | cut -c1-12)"
podman tag "${IMAGE_ID}" "${VERSION_TAG}"
podman untag "${TAG}" 2>/dev/null || true
podman tag "${IMAGE_ID}" "${TAG}"
log "tagged ${IMAGE_ID} as ${VERSION_TAG} and ${TAG}"

# A bare OCI archive has no repository field, so a load can leave it as
# "latest:latest". Clean up any stray bare tag under the lock, then assert
# it is actually gone -- a stray "latest:latest" left behind is exactly the
# ambiguous reference this cleanup exists to prevent.
podman rmi latest:latest >/dev/null 2>&1 || true
if podman image exists latest:latest; then
    log "stray bare 'latest:latest' tag survived cleanup"
    exit 1
fi

# Remove the superseded image now that the new one is fully in place. Not
# -f: a container may still be running against it (no-reboot upgrade case),
# and a failed rmi there is expected, not fatal. Under vfs there is no
# layer sharing, so leaving this around costs the app's full size again.
if [ -n "${OLD_ID}" ] && [ "${OLD_ID}" != "${IMAGE_ID}" ]; then
    podman rmi "${OLD_ID}" || log "superseded image ${OLD_ID} not removed (in use?)"
fi

# Written last, atomically, only once every step above succeeded. A crash,
# OOM kill or timeout anywhere before this point leaves no stamp, so the
# next boot re-imports -- writing it any earlier would reproduce the
# original bug in a new form (skipped forever after a half-finished import).
mkdir -p "${STAMP_DIR}"
printf '%s\n' "${WANT}" >"${STAMP}.new" && mv "${STAMP}.new" "${STAMP}"
log "done: ${TAG} -> ${IMAGE_ID}"

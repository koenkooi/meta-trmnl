SUMMARY = "Terminus worker (Sidekiq) as a single-process image-oci.bbclass container"
DESCRIPTION = "Same terminus package as terminus-web-container, different \
entrypoint (Sidekiq, not Puma) and no migrate/seed step -- ordered after the \
web container via the Quadlet unit's Requires=/After= (see \
recipes-images/trmnl/terminus-container-image.bb), matching \
terminus-worker.service's own After=terminus-web.service. Runs as root like \
terminus-web-container (see its own comment for why) -- the entrypoint fixes \
log/tmp/public ownership itself, then setpriv drops to terminus."
HOMEPAGE = "https://github.com/usetrmnl/terminus"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.adoc;md5=a2f2df8f44e621fed4cc67b4289eb4b6"

require recipes-images/trmnl/trmnl-container-base.bb
TRMNL_CONTAINER_TITLE = "terminus-worker"

# Same git binary requirement as terminus-web-container.bb -- Sidekiq boots
# the same Hanami app (config/sidekiq.rb requires hanami/boot), which hits
# config/settings.rb's eager `git rev-parse`/`git tag` backticks exactly
# the same way. See terminus-web-container.bb's own comment for the full
# Errno::ENOENT trace this was root-caused from.
#
# tzdata: same sidekiq-scheduler/et-orbi TZInfo::DataSourceNotFound as
# terminus-web-container.bb -- Sidekiq loads app/providers/sidekiq.rb too.
IMAGE_INSTALL:append = " terminus git util-linux-setpriv tzdata"

TERMINUS_APP_DIR = "${libdir}/terminus/app"

IMAGE_PREPROCESS_COMMAND += "terminus_worker_container_entrypoint"
terminus_worker_container_entrypoint () {
    install -d ${IMAGE_ROOTFS}${libexecdir}
    cat > ${IMAGE_ROOTFS}${libexecdir}/terminus-worker-entrypoint.sh << 'EOF'
#!/bin/sh
# Runs as root to fix log/tmp/public ownership (see terminus-web-container.bb's
# own comment for why), then setpriv drops to terminus for Sidekiq itself --
# Sidekiq boots the same Hanami providers (config/providers/mini_magick.rb
# included) so it needs the same writable app/tmp.
set -e
chown -R terminus:terminus ${TERMINUS_APP_DIR}/log ${TERMINUS_APP_DIR}/tmp \
    ${TERMINUS_APP_DIR}/public
cd ${TERMINUS_APP_DIR}
exec setpriv --reuid=terminus --regid=terminus --init-groups \
    bundle exec sidekiq -r ./config/sidekiq.rb
EOF
    chmod 0755 ${IMAGE_ROOTFS}${libexecdir}/terminus-worker-entrypoint.sh
}

OCI_IMAGE_ENTRYPOINT = "${libexecdir}/terminus-worker-entrypoint.sh"
OCI_IMAGE_WORKINGDIR = "${TERMINUS_APP_DIR}"

# Same env as terminus-web-container (verbatim copy of terminus.env) --
# Sidekiq needs the same DB/Redis/app config, just no HANAMI_PORT listener.
OCI_IMAGE_ENV_VARS = "\
    PATH=/usr/lib/terminus/vendor/bundle/ruby/4.0.0/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    RACK_ENV=production \
    HANAMI_ENV=production \
    HANAMI_SERVE_ASSETS=true \
    BUNDLE_GEMFILE=/usr/lib/terminus/app/Gemfile \
    BUNDLE_WITHOUT=development:quality:test:tools \
    GEM_PATH=/usr/lib/terminus/vendor/bundle/ruby/4.0.0:/usr/lib/ruby/gems/4.0.0 \
    RUBYOPT=-rrubygems \
    DATABASE_URL=postgres://terminus:terminus@127.0.0.1:5432/terminus \
    KEYVALUE_URL=redis://127.0.0.1:6379/0 \
    APP_SECRET=changeme-boot-test-only-not-for-production-use-64-chars-minimum-x \
    API_URI=http://127.0.0.1:2300 \
    HANAMI_PORT=2300 \
"

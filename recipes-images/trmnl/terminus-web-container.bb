SUMMARY = "Terminus web (Puma) as a single-process image-oci.bbclass container"
DESCRIPTION = "Wraps the terminus package (recipes-trmnl/terminus/terminus_0.68.0.bb) \
in meta-virtualization's container-base.bb shape -- no systemd inside the \
container, see the 'Design notes for Phase 2' section of meta-trmnl/README.md. \
The entrypoint folds terminus-db-migrate.service's chain (hanami db migrate, \
hanami db seed) in front of `bundle exec puma`, same idea as \
inker-backend-container's entrypoint. Runs as root like Inker's own \
containers (neither inker-backend-container nor inker-frontend-container set \
a Quadlet User=) -- confirmed live that terminus_0.68.0.bb's own pkg_postinst \
chown does not survive into this no-init image, and a build-time-only fixup \
via IMAGE_PREPROCESS_COMMAND does not either (root cause not chased further); \
the entrypoint fixes ownership itself at container start, as root, then \
setpriv drops to the terminus user for the actual app process -- same \
mechanism terminus-postgres-container.bb already uses to drop to postgres."
HOMEPAGE = "https://github.com/usetrmnl/terminus"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.adoc;md5=a2f2df8f44e621fed4cc67b4289eb4b6"

require recipes-images/trmnl/trmnl-container-base.bb
TRMNL_CONTAINER_TITLE = "terminus-web"

# config/settings.rb (pinned SRCREV 98d4f1e6) computes git_latest_sha/git_tag
# eagerly via backticked `git rev-parse`/`git tag` at Settings load time --
# every Hanami boot, not just a dev/console command. Confirmed live: with no
# git binary in this minimal container-base.bb image, Ruby's backtick raises
# Errno::ENOENT ("No such file or directory - git") and Hanami.setup never
# completes, crash-looping the whole container. console-base-image (what
# baremetal trmnl-image is built from) already installs git directly, which
# is why this never showed up there -- terminus_0.68.0.bb's own do_install
# also rm -rf's the app's .git dir, so `git rev-parse` still fails once git
# exists, but a failed git INVOCATION (nonzero exit, real stderr) is silent
# to Ruby's backtick, unlike a missing EXECUTABLE, which raises.
#
# tzdata: sidekiq-scheduler (pulled in by app/providers/sidekiq.rb, loaded
# on every Hanami boot, not just the worker) needs et-orbi/fugit/rufus-
# scheduler, which need real zoneinfo data -- confirmed live:
# TZInfo::DataSourceNotFound ("No source of timezone data could be found")
# with no /usr/share/zoneinfo in this minimal image. console-base-image
# (baremetal trmnl-image's base) already installs tzdata directly, same
# story as the git binary above.
IMAGE_INSTALL:append = " terminus git util-linux-setpriv tzdata"

TERMINUS_APP_DIR = "${libdir}/terminus/app"

IMAGE_PREPROCESS_COMMAND += "terminus_web_container_entrypoint"
terminus_web_container_entrypoint () {
    install -d ${IMAGE_ROOTFS}${libexecdir}
    cat > ${IMAGE_ROOTFS}${libexecdir}/terminus-web-entrypoint.sh << 'EOF'
#!/bin/sh
# Folds terminus-db-migrate.service's ExecStart chain in front of Puma --
# no systemd inside this container, see the recipe header. --no-dump: see
# terminus-db-migrate.service's own comment (root-owned structure.sql dump).
# Runs as root so it can fix log/tmp/public ownership (terminus_0.68.0.bb's
# own pkg_postinst chown does not survive into this image -- confirmed
# live), then setpriv drops to terminus for the real app process, same
# mechanism terminus-postgres-container.bb already uses.
set -e
id
chown -R terminus:terminus ${TERMINUS_APP_DIR}/log ${TERMINUS_APP_DIR}/tmp \
    ${TERMINUS_APP_DIR}/public
cd ${TERMINUS_APP_DIR}
AS_TERMINUS="setpriv --reuid=terminus --regid=terminus --init-groups"
$AS_TERMINUS bundle exec hanami db migrate --no-dump
$AS_TERMINUS bundle exec hanami db seed
exec $AS_TERMINUS bundle exec puma --config ./config/puma.rb
EOF
    chmod 0755 ${IMAGE_ROOTFS}${libexecdir}/terminus-web-entrypoint.sh
}

OCI_IMAGE_ENTRYPOINT = "${libexecdir}/terminus-web-entrypoint.sh"
OCI_IMAGE_WORKINGDIR = "${TERMINUS_APP_DIR}"
OCI_IMAGE_PORTS = "2300/tcp"

# Verbatim copy of terminus.env (recipes-trmnl/terminus/terminus/terminus.env)
# -- OCI_IMAGE_ENV_VARS is a plain whitespace-split list, so no value here
# may contain a space (none of terminus.env's do).
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

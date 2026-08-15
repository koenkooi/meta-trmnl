SUMMARY = "PostgreSQL for Terminus as a single-process image-oci.bbclass container"
DESCRIPTION = "Folds terminus-db-init.sh + terminus-db-create.sh (recipes-trmnl/ \
terminus/terminus/) into one entrypoint: initdb the data dir if empty, start \
postgres long enough to create the terminus role/db, stop it, then exec the \
real postgres server as PID 1. No systemd inside the container -- see the \
'Design notes for Phase 2' section of meta-trmnl/README.md. State (PGDATA) \
lives in the terminus-pgdata.volume Quadlet volume, see \
recipes-images/trmnl/terminus-container-image.bb."
HOMEPAGE = "https://www.postgresql.org/"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require recipes-images/trmnl/trmnl-container-base.bb
TRMNL_CONTAINER_TITLE = "terminus-postgres"

IMAGE_INSTALL:append = " postgresql postgresql-client postgresql-contrib util-linux-setpriv"

# postgres refuses to run (and refuses initdb) as root -- setpriv drops to
# the postgres system user for every postgres-owned step, same account
# terminus-db-init.sh/terminus-db-create.sh already relied on via `su -l
# postgres`, which needs PAM this minimal container image doesn't carry.
IMAGE_PREPROCESS_COMMAND += "terminus_postgres_container_entrypoint"
terminus_postgres_container_entrypoint () {
    install -d ${IMAGE_ROOTFS}${libexecdir}
    cat > ${IMAGE_ROOTFS}${libexecdir}/terminus-postgres-entrypoint.sh << 'EOF'
#!/bin/sh
set -e
PGDATA=/var/lib/postgresql/data
AS_PG="setpriv --reuid=postgres --regid=postgres --init-groups"

if [ ! -f "$PGDATA/PG_VERSION" ]; then
    mkdir -p "$PGDATA"
    chown postgres:postgres "$PGDATA"
    chmod 0700 "$PGDATA"
    $AS_PG initdb -D "$PGDATA" --auth-local=peer --auth-host=md5
fi

$AS_PG pg_ctl start -D "$PGDATA" -s -o "-p 5432" -w -t 60
role=$($AS_PG psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='terminus'")
[ "$role" = "1" ] || $AS_PG psql -c "CREATE ROLE terminus LOGIN PASSWORD 'terminus'"
db=$($AS_PG psql -tAc "SELECT 1 FROM pg_database WHERE datname='terminus'")
[ "$db" = "1" ] || $AS_PG createdb -O terminus terminus
$AS_PG pg_ctl stop -D "$PGDATA" -s -m fast

exec $AS_PG postgres -D "$PGDATA" -p 5432
EOF
    chmod 0755 ${IMAGE_ROOTFS}${libexecdir}/terminus-postgres-entrypoint.sh
}

OCI_IMAGE_ENTRYPOINT = "${libexecdir}/terminus-postgres-entrypoint.sh"
OCI_IMAGE_PORTS = "5432/tcp"

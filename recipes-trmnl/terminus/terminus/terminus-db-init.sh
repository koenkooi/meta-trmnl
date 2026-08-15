#!/bin/sh
# postgresql.service's own ExecStart (pg_ctl start) fails outright on an
# empty PGDATA -- meta-oe ships postgresql-setup for this but hardcodes
# --auth=ident for both local and host connections, which is incompatible
# with Terminus's password-based DATABASE_URL. Run our own initdb instead,
# split so local (peer, used by terminus-db-create.sh) and host (md5,
# used by Terminus itself) get the auth method each actually needs.
set -e
PGDATA=/var/lib/postgresql/data
[ -f "$PGDATA/PG_VERSION" ] && exit 0
mkdir -p "$PGDATA"
chown postgres:postgres "$PGDATA"
chmod 0700 "$PGDATA"
su -l postgres -c "initdb -D '$PGDATA' --auth-local=peer --auth-host=md5"

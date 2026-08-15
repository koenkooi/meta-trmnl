#!/bin/sh
# Idempotent: create the terminus role/database DATABASE_URL expects,
# once the cluster is actually up and accepting local connections.
set -e
role=$(su -l postgres -c "psql -tAc \"SELECT 1 FROM pg_roles WHERE rolname='terminus'\"")
[ "$role" = "1" ] || su -l postgres -c "psql -c \"CREATE ROLE terminus LOGIN PASSWORD 'terminus'\""
db=$(su -l postgres -c "psql -tAc \"SELECT 1 FROM pg_database WHERE datname='terminus'\"")
[ "$db" = "1" ] || su -l postgres -c "createdb -O terminus terminus"

#!/bin/sh
# Runs once, when the PostgreSQL volume is first initialised. Creates the read-only Power BI login.
# Flyway (V2) grants it access to the reporting views. Skipped if POWERBI_READER_PASSWORD is empty.
set -e
if [ -z "$POWERBI_READER_PASSWORD" ]; then
  echo "POWERBI_READER_PASSWORD not set: skipping powerbi_reader"
  exit 0
fi
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v pw="$POWERBI_READER_PASSWORD" <<'SQL'
create role powerbi_reader login password :'pw';
revoke all on schema public from powerbi_reader;
grant connect on database justify to powerbi_reader;
SQL

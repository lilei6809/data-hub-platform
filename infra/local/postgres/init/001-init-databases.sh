#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${APP_DB_USER}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${APP_DB_USER}', '${APP_DB_PASSWORD}');
    END IF;
END
\$\$;
EOSQL

for database_name in "${POSTGRES_TENANT_MANAGEMENT_DB}" "${POSTGRES_KEYCLOAK_DB}"; do
  database_exists="$(psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" -tAc "SELECT 1 FROM pg_database WHERE datname='${database_name}'")"

  if [ "${database_exists}" != "1" ]; then
    psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
      -c "CREATE DATABASE \"${database_name}\" OWNER \"${APP_DB_USER}\""
  fi
done

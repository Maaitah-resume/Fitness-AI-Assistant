#!/bin/bash
# Runs once, only when the postgres data volume is first created, via
# postgres's /docker-entrypoint-initdb.d/ convention. Creates one database
# per service that owns relational data (see docs/spring-boot-microservices-plan.md §3).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE user_db;
    CREATE DATABASE profile_db;
    CREATE DATABASE chat_db;
    CREATE DATABASE file_db;
EOSQL

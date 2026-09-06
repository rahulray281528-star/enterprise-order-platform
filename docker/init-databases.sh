#!/bin/bash
# Creates one logical database per service on first container start.
# Database-per-service: no service can read or write another service's tables,
# which is what keeps the boundaries real rather than aspirational.
set -e

for db in auth_db product_db inventory_db order_db payment_db notification_db; do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
      CREATE DATABASE $db;
      GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
done

echo "All service databases created."

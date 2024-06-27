#!/bin/bash

# Set PostgreSQL connection parameters
HOST="localhost"
PORT="5432"
DATABASE="authdb"
USER="authdbuser"

# Define the SQL command to drop all tables
SQL_COMMANDS=$(cat <<EOF
DO \$\$
DECLARE
    r RECORD;
BEGIN
    -- Loop through all tables in the current schema
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        -- Drop each table
        EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END \$\$;
EOF
)

# Execute the SQL command using psql
psql -h $HOST -p $PORT -d $DATABASE -U $USER -c "$SQL_COMMANDS"

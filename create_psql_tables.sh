#!/bin/bash

# Set PostgreSQL connection parameters
HOST="localhost"
PORT="5432"
DATABASE="authdb"
USER="authdbuser"

# Define the SQL commands to execute
SQL_COMMANDS=$(cat <<EOF
-- Your SQL commands here
CREATE TABLE roles (id SERIAL PRIMARY KEY, role_name VARCHAR(50) NOT NULL UNIQUE);
INSERT INTO roles (role_name) VALUES ('Standard'), ('Admin'), ('Moderator'), ('Guest');
CREATE TABLE users (id SERIAL PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE, email VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(100) NOT NULL, role_id INT REFERENCES roles(id) DEFAULT 1, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE bet_types (id SERIAL PRIMARY KEY, type VARCHAR(50) NOT NULL UNIQUE);
INSERT INTO bet_types (type) VALUES ('h2h'), ('spread');
CREATE TABLE status (id SERIAL PRIMARY KEY, status VARCHAR(50) NOT NULL UNIQUE);
INSERT into status (status) VALUES ('active'), ('playing'), ('settled');
CREATE TABLE league (
      id SERIAL PRIMARY KEY,
      name VARCHAR(50) NOT NULL,
      subleague_of INTEGER,
      CONSTRAINT fk_subleague
          FOREIGN KEY (subleague_of)
          REFERENCES league(id)
          ON DELETE SET NULL
      );
CREATE TABLE teams (
      id SERIAL PRIMARY key,
      team_name VARCHAR(70) NOT NULL,
      league_id INTEGER NOT NULL,
      CONSTRAINT fk_leagueid
          FOREIGN KEY (league_id)
          REFERENCES league(id)
      );
CREATE TABLE game (
      id SERIAL PRIMARY KEY,
      type_id INTEGER NOT NULL,
      CONSTRAINT fk_type
          FOREIGN KEY (type_id)
          REFERENCES bet_types(id),
      team1 INTEGER NOT NULL,
      CONSTRAINT fk_team1id
          FOREIGN KEY (team1)
          REFERENCES teams(id),
      odds1 NUMERIC(10,4) NOT NULL,
      line1 NUMERIC(10,4),
      team2 INTEGER NOT NULL,
      CONSTRAINT fk_team2id
          FOREIGN KEY (team2)
          REFERENCES teams(id),
      odds2 NUMERIC(10,4) NOT NULL,
      line2 NUMERIC(10,4),
      last_update TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      game_start_time TIMESTAMP NOT NULL,
      status_id INTEGER NOT NULL,
      CONSTRAINT fk_status
          FOREIGN KEY (status_id)
          REFERENCES status(id),
      winner VARCHAR(50) DEFAULT NULL,
      league_id INTEGER NOT NULL,
      CONSTRAINT fk_league
          FOREIGN KEY (league_id)
          REFERENCES league(id)
      );
CREATE TABLE group_role (id SERIAL PRIMARY KEY, role VARCHAR(20) NOT NULL UNIQUE);
INSERT INTO group_role (role) VALUES ('group_creator'), ('group_admin'), ('group_user');
CREATE TABLE groups (
       id SERIAL PRIMARY KEY,
       name VARCHAR(100) NOT NULL UNIQUE,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       end_date TIMESTAMP NOT NULL,
       is_active BOOLEAN NOT NULL DEFAULT TRUE,
       starting_cash NUMERIC(10,3) NOT NULL DEFAULT 100.000
       );
CREATE TABLE accounts (
      group_id INTEGER NOT NULL,
      user_id INTEGER NOT NULL,
      current_cash NUMERIC(10,3) NOT NULL,
      group_role_id INTEGER NOT NULL,
      CONSTRAINT pk_accounts PRIMARY KEY (group_id, user_id),
      CONSTRAINT fk_group
          FOREIGN KEY (group_id)
          REFERENCES "groups"(id),
      CONSTRAINT fk_user
          FOREIGN KEY (user_id)
          REFERENCES users(id),
      CONSTRAINT fk_group_role
          FOREIGN KEY (group_role_id)
          REFERENCES group_role(id),
      CONSTRAINT default_current_cash
          CHECK (current_cash >= 0)
      );
CREATE TABLE bets (
      id SERIAL PRIMARY KEY,
      group_id INTEGER NOT NULL,
      CONSTRAINT fk_group_id
          FOREIGN KEY (group_id)
          REFERENCES groups(id),
      user_id INTEGER NOT NULL,
      CONSTRAINT fk_user_id
          FOREIGN KEY (user_id)
          REFERENCES users(id),
      game_id INTEGER NOT NULL,
      CONSTRAINT fk_game_id
          FOREIGN KEY (game_id)
          REFERENCES game(id),
      wagered NUMERIC(10,3) NOT NULL,
      amount_to_win NUMERIC(10,3) NOT NULL,
      picked_winner INTEGER NOT NULL,
      CONSTRAINT fk_picked_winner
          FOREIGN KEY (picked_winner)
          REFERENCES teams(id),
      time_placed TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      been_distributed BOOLEAN NOT NULL DEFAULT FALSE,
      is_parlay BOOLEAN NOT NULL DEFAULT FALSE
      );

CREATE OR REPLACE FUNCTION set_default_current_cash()
RETURNS TRIGGER AS \$\$
BEGIN
    IF NEW.current_cash IS NULL THEN
        SELECT starting_cash INTO NEW.current_cash FROM "groups" WHERE id = NEW.group_id;
    END IF;
    RETURN NEW;
END;
\$\$ LANGUAGE plpgsql;

CREATE TRIGGER set_default_current_cash_trigger
      BEFORE INSERT ON accounts
      FOR EACH ROW
      EXECUTE FUNCTION set_default_current_cash();
EOF
)

# Execute the SQL commands using psql
psql -h $HOST -p $PORT -d $DATABASE -U $USER -c "$SQL_COMMANDS"

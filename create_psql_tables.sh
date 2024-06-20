#!/bin/bash

# Set PostgreSQL connection parameters
HOST="localhost"
PORT="5432"
DATABASE="authdb"
USER="authdbuser"

# Define the SQL commands to execute
SQL_COMMANDS=$(cat <<EOF
-- Your SQL commands here
CREATE TABLE users (username VARCHAR(50) PRIMARY KEY, email VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(100) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE bet_types (type VARCHAR(20) PRIMARY KEY);
INSERT INTO bet_types (type) VALUES ('h2h');
CREATE TABLE statuses (status VARCHAR(20) PRIMARY KEY);
INSERT into statuses (status) VALUES ('upcoming'), ('playing'), ('settled');
CREATE TABLE leagues (
      name VARCHAR(50) PRIMARY KEY,
      subleague_of VARCHAR(50),
      CONSTRAINT fk_subleague
          FOREIGN KEY (subleague_of)
          REFERENCES leagues(name)
          ON DELETE SET NULL
      );
CREATE TABLE teams (
      team_name VARCHAR(50) PRIMARY KEY,
      league VARCHAR(50) NOT NULL,
      CONSTRAINT fk_league
          FOREIGN KEY (league)
          REFERENCES leagues(name)
      );
CREATE TABLE games (
      game_id SERIAL PRIMARY KEY,
      team1 VARCHAR(50) NOT NULL,
      CONSTRAINT fk_team1
          FOREIGN KEY (team1)
          REFERENCES teams(team_name),
      odds1 NUMERIC(10,4) NOT NULL,
      line1 NUMERIC(10,4),
      team2 VARCHAR(50) NOT NULL,
      CONSTRAINT fk_team2
          FOREIGN KEY (team2)
          REFERENCES teams(team_name),
      odds2 NUMERIC(10,4) NOT NULL,
      line2 NUMERIC(10,4),
      last_update TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      game_start_time TIMESTAMP NOT NULL,
      status VARCHAR(20) NOT NULL,
      CONSTRAINT fk_status
          FOREIGN KEY (status)
          REFERENCES statuses(status),
      winner VARCHAR(50) DEFAULT NULL,
      CONSTRAINT fk_winner
          FOREIGN KEY (winner)
          REFERENCES teams(team_name),
      league VARCHAR(50) NOT NULL,
      CONSTRAINT fk_league
          FOREIGN KEY (league)
          REFERENCES leagues(name)
      );
CREATE TABLE group_roles (group_role_id SERIAL PRIMARY KEY, role VARCHAR(20) NOT NULL UNIQUE);
INSERT INTO group_roles (role) VALUES ('group_creator'), ('group_admin'), ('group_user');
CREATE TABLE groups (
       group_name VARCHAR(100) PRIMARY KEY,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       end_date TIMESTAMP NOT NULL,
       is_active BOOLEAN NOT NULL DEFAULT TRUE,
       has_been_deleted BOOLEAN NOT NULL DEFAULT FALSE,
       starting_cash NUMERIC(10,3) NOT NULL DEFAULT 100.000
       );
CREATE INDEX idx_group_end_date ON groups(end_date);
CREATE TABLE accounts (
      group_name VARCHAR(100) NOT NULL,
      username VARCHAR(50) NOT NULL,
      current_cash NUMERIC(10,3) NOT NULL,
      group_role_id INTEGER NOT NULL DEFAULT 3,
      CONSTRAINT pk_accounts PRIMARY KEY (group_name, username),
      CONSTRAINT fk_group_name
          FOREIGN KEY (group_name)
          REFERENCES groups(group_name),
      CONSTRAINT fk_username
          FOREIGN KEY (username)
          REFERENCES users(username),
      CONSTRAINT fk_group_role
          FOREIGN KEY (group_role_id)
          REFERENCES group_roles(group_role_id),
      CONSTRAINT default_current_cash
          CHECK (current_cash >= 0)
      );
CREATE INDEX idx_accounts_group_name ON accounts(group_name);
CREATE TABLE bets (
      bet_id SERIAL PRIMARY KEY,
      type VARCHAR(20),
      CONSTRAINT fk_type
          FOREIGN KEY (type)
          REFERENCES bet_types(type),
      group_name VARCHAR(100) NOT NULL,
      CONSTRAINT fk_group_name
          FOREIGN KEY (group_name)
          REFERENCES groups(group_name),
      username VARCHAR(50) NOT NULL,
      CONSTRAINT fk_username
          FOREIGN KEY (username)
          REFERENCES users(username),
      game_id INTEGER NOT NULL,
      CONSTRAINT fk_game_id
          FOREIGN KEY (game_id)
          REFERENCES games(game_id),
      wagered NUMERIC(10,3) NOT NULL,
      amount_to_win NUMERIC(10,3) NOT NULL,
      picked_winner VARCHAR(50) NOT NULL,
      CONSTRAINT fk_picked_winner
          FOREIGN KEY (picked_winner)
          REFERENCES teams(team_name),
      time_placed TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      been_distributed BOOLEAN NOT NULL DEFAULT FALSE,
      is_parlay BOOLEAN NOT NULL DEFAULT FALSE
      );

CREATE OR REPLACE FUNCTION set_default_current_cash()
RETURNS TRIGGER AS \$\$
BEGIN
    IF NEW.current_cash IS NULL THEN
        SELECT starting_cash INTO NEW.current_cash FROM "groups" WHERE group_name = NEW.group_name;
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

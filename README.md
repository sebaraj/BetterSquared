languages: Java

db: postgres@16

tech/protocols: nginx (ingress), rabbitmq, gRPC, HTTP(S), SMTP, JWTs, 

build/deployment tools: Maven (manage build process/dependencies), Docker (containerizing), k8s, minikube, k9s


## Deploying:

### Gateway Service:



### Auth Service:
- Create .env file in ./authservice with appropriate credentials for:
  - `DB_HOST, DB_USER, DB_PASSWORD, DB_NAME, DB_PORT`
  - 127.0.0.1 is localhost and 5432 is postgres default port


### Email Service:


### PostgreSQL
- Install PostgreSQL
    - `brew install postgresql@15`
    - `initdb /usr/local/var/postgres/`
- Start PostgreSQL
    - `brew services start postgresql` (for postgres to run as background service)
    - `pg_ctl -D /usr/local/var/postgres/ start` (for postgres to run in the foreground)
    - `psql postgres` (within PostgreSQL CLI)
    - `CREATE USER authdbuser WITH PASSWORD 'testauth' CREATEDB;`
    - `CREATE DATABASE authdb with OWNER = authdbuser;`
    - `\q`
    - `psql -h localhost -d authdb -U authdbuser -p 5432`
    - `CREATE TABLE users (id SERIAL PRIMARY KEY, username VARCHAR(50) NOT NULL, email VARCHAR(100) NOT NULL, password VARCHAR(100) NOT NULL);`
    - 
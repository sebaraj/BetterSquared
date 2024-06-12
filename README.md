languages: Java

db: postgres@16

tech/protocols: nginx (ingress), rabbitmq, gRPC, Protobufs, HTTP(S), SMTP, JWTs, 

build/deployment tools: Maven (manage build process/dependencies), Docker (containerizing), k8s, minikube, k9s


## Deploying:

### Gateway Service:



### Auth Service:
- Define env variables for the following in appropriate yaml files in ./authservice/manifests:
  - `DB_HOST, DB_USER, DB_PASSWORD, DB_NAME, DB_PORT, HTTP_SERVER_HOST, HTTP_SERVER_PORT, HTTP_SERVER_BACKLOG, THREAD_POOL_CORE_SIZE, THREAD_POOL_MAX_SIZE, THREAD_POOL_KEEP_ALIVE, JWT_SECRET`


### Email Service:


### PostgreSQL
- Install PostgreSQL
    - `brew install postgresql@16`
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
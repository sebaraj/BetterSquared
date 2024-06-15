languages: Java

db: postgres@16

tech/protocols: nginx (ingress), rabbitmq, gRPC, Protobufs, HTTP(S), SMTP, JWTs, 

build/deployment tools: Maven (manage build process/dependencies), Docker (containerizing), k8s, minikube, k9s


## Deploying:

### Gateway Service:
- Define env variables for the following in appropriate (configmap) yaml files in ./gatewayservice/manifests:
  - `GATEWAY_HTTP_SERVER_HOST, GATEWAY_HTTP_SERVER_PORT, GATEWAY_HTTP_SERVER_BACKLOG, AUTH_SERVICE_HOST, AUTH_SERVICE_PORT, GATEWAY_THREAD_POOL_CORE_SIZE, GATEWAY_THREAD_POOL_MAX_SIZE, GATEWAY_THREAD_POOL_KEEP_ALIVE, RABBITMQ_HOST, RABBITMQ_PORT`
- Add mapping for 127.0.0.1 to desired host in /etc/hosts


### Auth Service:
- Define env variables for the following in appropriate (configmap/secret) yaml files in ./authservice/manifests:
  - `AUTH_DB_HOST, AUTH_DB_USER, AUTH_DB_PASSWORD, AUTH_DB_NAME, AUTH_DB_PORT, AUTH_HTTP_SERVER_HOST, AUTH_HTTP_SERVER_PORT, AUTH_HTTP_SERVER_BACKLOG, AUTH_THREAD_POOL_CORE_SIZE, AUTH_THREAD_POOL_MAX_SIZE, AUTH_THREAD_POOL_KEEP_ALIVE, AUTH_JWT_SECRET, RABBITMQ_HOST, RABBITMQ_PORT`

### RabbitMQ:

### Email Service:
 - Define env variables for the following in appropriate (configmap/secret) yaml files in ./authservice/manifests:
  - `SMTP_SERVER_SENDER_EMAIL, SMTP_SERVER_SENDER_HOST, SMTP_SERVER_SENDER_PORT, SMTP_SERVER_SENDER_USER, RABBITMQ_HOST, RABBITMQ_PORT, SMTP_SERVER_PASSWORD`


### PostgreSQL
- Install PostgreSQL
    - `brew install postgresql@16`
    - `initdb /usr/local/var/postgres/`
- Start PostgreSQL
    - `brew services start postgresql` (for postgres to run as background service)
    - `pg_ctl -D /usr/local/var/postgres/ start` (for postgres to run in the foreground)
- Intialize DB
    - `psql postgres` 
    - `CREATE USER authdbuser WITH PASSWORD 'testauth' CREATEDB;`
    - `CREATE DATABASE authdb with OWNER = authdbuser;`
    - `\q`
    - `psql -h localhost -d authdb -U authdbuser -p 5432`
    - `CREATE TABLE roles (id SERIAL PRIMARY KEY, role_name VARCHAR(50) NOT NULL UNIQUE);`
    - `INSERT INTO roles (role_name) VALUES ('Standard'), ('Admin'), ('Moderator'), ('Guest');`
    - `CREATE TABLE users (id SERIAL PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE, email VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(100) NOT NULL, role_id INT REFERENCES roles(id) DEFAULT 1, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);`


### Starting Backend
- `./start-backend.sh` in this project root directory, with sudo permissions

## Client-Accessible Paths


## Monitoring Backend
### RabbitMQ
- http://rabbitmq-manager.com, after reconfiguring 127.0.0.1 to localhost in /etc/hosts, with username "guest" and password "guest"

### Kubernetes
- I will always sing `k9s` praises and highly recommend it
- Install with `brew install derailed/k9s/k9s`
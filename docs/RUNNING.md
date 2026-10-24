# Running the Fitness AI Assistant

The app is a React frontend (Vite) in front of seven Spring Boot services,
all reached through one API gateway on port 8080.

```
browser ──► Vite dev server :5173 ──/api──► api-gateway :8080 ──► services
```

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Docker Desktop | recent, **running** | backend services + infrastructure |
| Node.js + npm | 18+ | frontend |
| Gemini API key | — | chat replies, embeddings, GraphRAG extraction (optional to start) |

Java and Maven are **not** required on your machine for the Docker setup: each
service's `Dockerfile` builds itself inside a Maven container.

> **Docker Hub sign-in:** the first build pulls `maven`, `eclipse-temurin`,
> `postgres`, `redis`, `qdrant`, `neo4j` and `minio` images. If you see
> `authentication required` or `429 Too Many Requests`, sign in to Docker
> Desktop (or `docker login`) and retry.

## 1. Configure

```bash
cp infra/.env.example infra/.env
```

Edit `infra/.env` and set `GEMINI_API_KEY`. Without a key the stack still
starts, but chat answers with an "AI unavailable" fallback.

## 2. Start the backend

```bash
cd infra
docker compose up -d --build
```

The first build takes several minutes (Maven dependencies are downloaded per
service). Check that everything is up:

```bash
docker compose ps
docker compose logs -f api-gateway      # or any service name
```

`knowledge-service` may restart a few times until Qdrant is ready. That is
expected.

## 3. Start the frontend

```bash
cd frontend
npm install        # first time only
npm run dev
```

Open **http://localhost:5173**. Vite proxies every `/api` call to the gateway
at `http://127.0.0.1:8080`. To point it elsewhere, set `VITE_API_TARGET` in
`frontend/.env`.

## 4. Create an admin user

New accounts register as `USER`. To make your first admin, register in the
UI and then promote that account:

```bash
docker compose exec postgres psql -U fitnessai -d user_db \
  -c "UPDATE users SET role = 'ADMIN' WHERE username = 'your-username';"
```

Log out and back in so the new role is in your token. After that, admins can
change roles from the Admin panel.

## Ports and dashboards

Only the gateway is published for the services; they are not reachable
directly from the host, so all traffic goes through the gateway's auth and
rate limiting.

| What | URL | Login |
|---|---|---|
| Frontend | http://localhost:5173 | — |
| API gateway | http://localhost:8080 | JWT from `/api/v1/auth/login` |
| Neo4j browser (knowledge graph) | http://localhost:7474 | `neo4j` / `fitnessai123` |
| MinIO console (generated/uploaded files) | http://localhost:9001 | `fitnessai` / `fitnessai123` |
| Qdrant dashboard (vectors) | http://localhost:6333/dashboard | — |
| PostgreSQL | `localhost:5432` | `fitnessai` / `fitnessai` |
| Redis | `localhost:6379` | — |

Inside the compose network the services listen on: user 8081, profile 8082,
chat 8083, knowledge 8084, file 8085, charts 8086.

These credentials are development defaults. Change them before deploying
anywhere shared.

## Running tests

Each service is an independent Maven project. With JDK 21 and Maven 3.9+
installed:

```bash
cd backend/services/user-service      # any service
mvn test
```

## Stopping and resetting

```bash
cd infra
docker compose down          # stop, keep data
docker compose down -v       # stop and delete all data (DBs, vectors, files, JWT key)
```

Deleting the volumes also deletes the JWT signing key, which logs every user out.

## Legacy Python backend

`backend/app.py` / `run_server.py` is the original FastAPI monolith, kept
until the migration's final decommission phase (see
[spring-boot-microservices-plan.md](spring-boot-microservices-plan.md)). The
frontend no longer uses it, so you don't need to run it.

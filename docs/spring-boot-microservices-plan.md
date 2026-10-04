# Fitness AI Assistant — Spring Boot Microservices Migration Plan

Target: replace the current Python/FastAPI monolith with a Java/Spring Boot
microservices backend, keeping the React frontend. Adds GraphRAG, a
conversational memory layer, and Redis-backed caching.

This document is a plan, not an implementation. Where I made a call between
reasonable options, it's marked **Decision** with the reasoning, so you can
override it before work starts.

---

## 1. Service map

You asked for five capabilities: chat (user+admin), profile management
(user), user management (admin), charts (user+admin), and file
generation/document processing in chat. Two more services are needed to
support those cleanly:

| # | Service | Owns | Used by |
|---|---------|------|---------|
| — | **api-gateway** | Routing, JWT pre-validation, rate limiting | Everything (single entry point) |
| 1 | **user-service** | Identity: accounts, credentials, roles. Covers both auth (register/login/JWT issuing) *and* admin user management — see **Decision 1**. | All services (JWT verification), frontend (login/admin panel) |
| 2 | **profile-service** | Fitness profile: age, gender, goal, level, activity flag | chat-service (personalization), charts-service |
| 3 | **chat-service** | Conversation orchestration, chat history, LLM calls, short-term memory | Frontend, charts-service (activity stats) |
| 4 | **knowledge-service** | RAG: vector store + sparse index + **GraphRAG** graph store, retrieval API | chat-service, file-service |
| 5 | **file-service** | Document ingestion (PDF parsing → chunks → knowledge-service) and document *generation* (workout/nutrition PDFs produced from chat) | chat-service, frontend (download), knowledge-service |
| 6 | **charts-service** | Aggregated analytics/dashboards, own view + admin global view | Frontend |

**Decision 1 — fold auth into user-service, not a separate service.**
Credentials, identity, and role management all live on the same `users`
table and change together. Splitting "auth" from "user management" would
just mean two services fighting over one table. `user-service` exposes
both the public `/auth/*` endpoints and the admin-only `/admin/users/*`
endpoints.

**Decision 2 — knowledge-service is new, not explicitly in your list.**
GraphRAG, the vector store, and the sparse index need a home, and both
chat-service (answering questions) and file-service (indexing uploads)
need to call into it. Embedding RAG logic inside chat-service would force
file-service to either duplicate it or reach into chat-service's internals.
A dedicated retrieval service keeps "how do we find context" separate from
"what do we do with context."

---

## 2. Architecture diagram

```
                         ┌─────────────────────┐
                         │   React Frontend     │
                         └──────────┬───────────┘
                                    │ HTTPS (JWT bearer)
                         ┌──────────▼───────────┐
                         │     api-gateway       │  Spring Cloud Gateway
                         │  (routing, JWT check, │
                         │   rate limit via Redis)│
                         └──────────┬───────────┘
          ┌───────────┬────────────┼────────────┬───────────┬───────────┐
          ▼           ▼            ▼             ▼           ▼           ▼
   ┌────────────┐┌───────────┐┌───────────┐┌───────────┐┌──────────┐┌───────────┐
   │user-service││profile-   ││chat-      ││file-      ││charts-   ││knowledge- │
   │(auth+admin)││service    ││service    ││service    ││service   ││service    │
   └─────┬──────┘└─────┬─────┘└─────┬─────┘└─────┬─────┘└────┬─────┘└─────┬─────┘
         │             │            │ ▲           │           │            │
         │             │            │ │           └──────────▶│ (indexing) │
         │             │            │ └───────────────────────┼────────────┘
         │             │            │   (retrieval calls)     │
         ▼             ▼            ▼                         ▼
     Postgres      Postgres     Postgres                  Qdrant + Neo4j
    (users db)    (profile db) (chat db)                (vectors + graph)

                         ┌─────────────────────┐
                         │        Redis         │ ← shared: sessions, rate
                         │                       │   limiting, short-term chat
                         └─────────────────────┘    memory, charts cache
```

Each box = its own Spring Boot app, its own database, its own deploy
lifecycle. No service reaches into another's database directly.

---

## 3. Data ownership

| Service | Store | Key tables/collections |
|---|---|---|
| user-service | PostgreSQL | `users (id, username, email, password_hash, role, created_at)` |
| profile-service | PostgreSQL | `profiles (user_id, age, gender, goal, level, profile_active)` |
| chat-service | PostgreSQL | `recent_chats`, `chat_history` |
| knowledge-service | Qdrant (vectors, dense+sparse) + Neo4j (graph) | `fitness_docs` collection; `(:Exercise)-[:TARGETS]->(:MuscleGroup)` etc. |
| file-service | PostgreSQL (metadata) + object storage | `documents (id, owner_id, type, status, storage_key)` + MinIO bucket |
| charts-service | No DB of its own initially (see §6) | — |

**Decision 3 — PostgreSQL, not SQLite.** SQLite is a single-file,
single-writer database; it doesn't work once multiple independent
processes (services) need concurrent write access. Each service gets its
own schema (dev: one Postgres instance, multiple schemas; prod: can split
to separate instances later without code changes).

Cross-service references (e.g., `profiles.user_id`) are **logical only** —
no foreign keys across service boundaries. profile-service trusts the
`user_id` claim from a validated JWT; it never queries user-service's table
directly.

---

## 4. Cross-cutting: GraphRAG, memory, and caching

### 4.1 GraphRAG (knowledge-service)

Keep the existing hybrid retrieval (dense OpenAI embeddings + sparse
BM25/TF-IDF, fused with RRF in Qdrant) as the "local search" layer, and add
a graph layer on top rather than replacing it — full community-report
GraphRAG (à la Microsoft's reference implementation) is expensive to build
and maintain; a lighter entity-graph layer gets most of the benefit for a
domain this size (exercises, muscle groups, equipment, goals, nutrition).

- **Ingestion**: when file-service hands knowledge-service a parsed
  document chunk, an extraction step (LLM call with a fitness-domain
  prompt) pulls `(entity, relationship, entity)` triples — e.g.
  `(Squat, TARGETS, Quadriceps)`, `(Squat, REQUIRES, Barbell)` — upserted
  into Neo4j alongside the existing vector upsert.
- **Retrieval**: a query does two lookups in parallel —
  1. today's hybrid vector+sparse search → candidate text chunks.
  2. entity match on the query → 1–2 hop graph traversal → structured
     facts (e.g., all exercises targeting a muscle group the user asked
     about).
  Both are merged into the context passed to the chat LLM call.
- **Decision 4 — Neo4j** for the graph store (mature, good Spring Data
  Neo4j support, Cypher is a good fit for "find exercises targeting X
  that don't require equipment Y" style queries).

### 4.2 Memory (chat-service)

Two tiers, not one:

- **Short-term** (current conversation): the last N turns, read on every
  message. Today this means a Postgres query per turn; move the *active*
  window into Redis (`chat:{chatId}:window`, capped list, TTL-refreshed)
  so hot conversations don't round-trip to Postgres for every reply.
  Postgres (`chat_history`) remains the durable log.
- **Long-term** (facts about the user across sessions): periodically
  (e.g., every N messages, async) summarize the conversation with an LLM
  call into durable facts ("prefers home workouts," "knee injury,"
  "goal: fat loss") and store them as embeddings in knowledge-service,
  namespaced by `user_id`. Retrieval merges these alongside document
  context — the user's own history becomes part of their RAG context.
- **Decision 5** — build this on **Spring AI's `ChatMemory` abstraction**
  rather than hand-rolling it. Spring AI (Spring's official AI
  integration library) ships `MessageWindowChatMemory` for the short-term
  tier and has `VectorStore`-backed patterns (including a Qdrant
  `VectorStore` implementation) that map directly onto the long-term tier.
  It also has first-class `Advisor` hooks for RAG, which is exactly the
  "merge retrieved context into the prompt" step chat-service needs — this
  one library covers most of §4.1's chat-side wiring and both memory
  tiers, so it's worth adopting rather than writing the retrieval-merge
  logic from scratch in each service.

### 4.3 Redis — what it's actually used for

Redis isn't a service you call directly from the frontend; it's shared
infrastructure. Concretely:

| Use | Service | Pattern |
|---|---|---|
| Short-term chat memory | chat-service | capped list per `chat_id`, TTL |
| Rate limiting | api-gateway | token-bucket counters per user/IP |
| JWT logout/blacklist | user-service / gateway | set of revoked token IDs, TTL = token expiry |
| Charts aggregate cache | charts-service | cache key per query, TTL ~60s |
| Hot profile/user lookups | profile-service, user-service | cache-aside, short TTL |

No service should treat Redis as a system of record — everything in it is
derived/rebuildable from Postgres, Qdrant, or Neo4j.

---

## 5. Chat service detail (user + admin)

- **User-facing**: `POST /chat/send`, `GET /chat/recent`, `GET
  /chat/{chatId}/messages`, `DELETE /chat/{chatId}` — same shape as the
  current FastAPI endpoints, scoped to the caller's own `user_id` from the
  JWT.
- **Admin-facing**: `GET /chat/admin/users/{userId}/chats` (view any user's
  conversations — for support/moderation), `GET /chat/admin/stats` (volume,
  active conversations — or this can live in charts-service instead and
  just call chat-service's internal stats endpoint; recommend the latter
  to avoid charts logic creeping into chat-service).
- Flow for `POST /chat/send`: validate JWT → load short-term memory window
  from Redis (fallback to Postgres if cold) → call knowledge-service
  `/retrieve` (hybrid + graph context) → call OpenAI via Spring AI
  `ChatClient` with system prompt + context + history → persist turn to
  Postgres → update Redis window → return reply.
- If the user asks the assistant to produce a document ("make me a PDF of
  this plan"), chat-service doesn't generate the file itself — it extracts
  the structured plan from the LLM response and calls file-service's
  `/files/generate` endpoint, returning the resulting file reference/link
  in its response.

## 6. Charts service detail (user + admin)

- **User view**: `GET /charts/me` — own profile trend, chat activity,
  goal progress (whatever profile-service/chat-service expose).
- **Admin view**: `GET /charts/admin/overview` — totals and breakdowns
  across all users (signups over time, role distribution, goal/level
  distribution, chat volume, active users) — "show all the user data and
  status" from your ask.
- **Decision 6 — no dedicated database for charts-service initially.**
  It calls read-only internal endpoints on user-service, profile-service,
  and chat-service, and caches results in Redis (TTL ~30–60s so an admin
  dashboard isn't hammering three services on every refresh). This is the
  simplest thing that works at this scale.
  - **Scaling note**: if usage grows enough that live aggregation gets
    slow, the standard next step is event-driven materialized views —
    each service publishes domain events (`UserRegistered`,
    `ChatMessageSent`, `ProfileUpdated`) to a broker (RabbitMQ/Kafka), and
    charts-service builds its own read-optimized store from the stream.
    Don't build this upfront; it's real complexity that isn't justified
    until the simple version is actually too slow.

## 7. File service detail (upload + generation)

Two directions through the same service:

- **Ingestion** (`POST /files/upload`): accept a PDF, parse it (Apache
  PDFBox/Tika instead of the current PyPDFLoader), chunk it, hand chunks
  to knowledge-service for indexing (vector + graph extraction), store
  metadata (`owner`, `filename`, `status`) in file-service's own table.
- **Generation** (`POST /files/generate`): given structured content from
  chat-service (e.g., a workout plan), render a PDF (Apache PDFBox/iText)
  or DOCX (Apache POI) and store it in object storage, returning a
  download URL. `GET /files/{id}/download` streams it back out.
- **Decision 7 — MinIO** for object storage (S3-compatible API, self-hosted,
  no cloud dependency, same client code if you later point it at real S3).

---

## 8. Security: JWT across services

Current system (Python) signs JWTs with a single shared HMAC secret
(`HS256`) that every component needs to know. That doesn't scale cleanly
across independently-deployed services.

**Decision 8 — move to RS256** (asymmetric). user-service holds the
private key and signs tokens; it exposes a JWKS endpoint
(`/.well-known/jwks.json`) with the public key. Every other service
configures Spring Security's OAuth2 Resource Server against that JWKS URL
and verifies tokens independently — no shared secret to distribute or
rotate across six codebases. The gateway can still do a cheap
"is this even a well-formed, non-expired token" check up front and reject
garbage early, but each service re-validates — don't trust the gateway as
the only checkpoint (defense in depth matters more, not less, once you have
six network-exposed services instead of one).

Role checks (`user` vs `admin`) stay as a claim in the token, same pattern
as today — `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints instead
of the current `require_admin` FastAPI dependency.

---

## 9. Tech stack

| Concern | Choice |
|---|---|
| Language/runtime | Java 21 (LTS), Spring Boot 3.x |
| Gateway | Spring Cloud Gateway |
| Inter-service calls | Spring `WebClient` / OpenFeign (sync REST) |
| Cross-service events (phase 2+) | RabbitMQ — only once charts-service needs it (§6) |
| Auth | Spring Security, OAuth2 Resource Server, JWT (RS256) |
| Relational data | PostgreSQL, Spring Data JPA |
| Cache/sessions | Redis, Spring Data Redis |
| Vector search | Qdrant (reused from current system), Spring AI `QdrantVectorStore` |
| Graph (GraphRAG) | Neo4j, Spring Data Neo4j |
| LLM integration | **Spring AI** — `ChatClient`, `ChatMemory`, RAG `Advisor` |
| Object storage | MinIO (S3-compatible) |
| PDF/doc parsing | Apache PDFBox / Tika |
| PDF/doc generation | Apache PDFBox or iText; Apache POI for DOCX |
| Dev orchestration | Docker Compose |
| Prod orchestration | Kubernetes (later — see §11; not needed to start) |

---

## 10. Repo layout

```
backend/
  services/
    api-gateway/
    user-service/
    profile-service/
    chat-service/
    knowledge-service/
    file-service/
    charts-service/
  app.py, db.py, routers/, ...   # existing Python/FastAPI monolith — stays
                                   # live until the strangler-fig cutover (§11)
                                   # retires it endpoint by endpoint
infra/
  docker-compose.yml       # postgres(es), redis, qdrant, neo4j, minio, rabbitmq (phase 2+)
frontend/                  # existing React app — mostly unchanged, see §12
```

Each service is its own Maven/Gradle module with its own `Dockerfile`,
independently buildable and deployable. Not a single fat multi-module
build where every service rebuilds on every change.

---

## 11. Migration phases (strangler fig, not big-bang)

The Python backend keeps running and serving the frontend until each piece
has a working Java replacement behind the gateway — don't take the whole
app down to do this rewrite.

| Phase | Deliverable | Depends on |
|---|---|---|
| 0 | Finalize API contracts (OpenAPI specs per service), DB schemas, event names. Stand up `infra/docker-compose.yml` (Postgres, Redis, Qdrant, Neo4j, MinIO). | — |
| 1 | `api-gateway` + `user-service` (register/login/JWT/admin user mgmt). Frontend's `/api/v1/auth/*` and `/api/v1/users/*` calls repointed to the gateway. | Phase 0 |
| 2 | `profile-service`. Frontend profile calls repointed. | Phase 1 (JWT) |
| 3 | `knowledge-service` baseline — reach parity with current Python hybrid RAG (reindex existing PDFs into the new Qdrant collection via the Java service). GraphRAG is **not** in this phase. | Phase 1 |
| 4 | `chat-service` — full chat flow using knowledge-service for retrieval, Redis for short-term memory. Frontend chat calls repointed; this is the point the Python `chat_logic.py`/RAG code can be retired. | Phases 1–3 |
| 5 | `file-service` — upload/ingest (calls knowledge-service) and generation. Frontend upload calls repointed. | Phases 1, 3 |
| 6 | `charts-service` — aggregation + admin dashboard. New frontend screens. | Phases 1, 2, 4 |
| 7 | GraphRAG — add Neo4j extraction/traversal into knowledge-service, additive to phase 3's baseline. | Phase 3 |
| 8 | Long-term memory tier (periodic summarization → knowledge-service). | Phases 4, 7 |
| 9 | Decommission the Python/FastAPI backend once every endpoint the frontend uses has a Java equivalent live. | All above |

Each phase ends with the frontend pointed at the new service for that
slice of functionality and the old Python endpoint either removed or left
as a dead fallback — don't run both as the source of truth for the same
data at the same time.

---

## 12. Frontend impact

Smaller than it sounds, because of how the current frontend is already
structured:

- `frontend/src/api.js` already centralizes the base fetch + bearer-token
  logic — only the base URL (→ api-gateway) and per-service path prefixes
  need to change, not the auth flow itself.
- New screens needed: charts/dashboard (user + admin), file
  upload-status/download UI (beyond the existing chat-attach flow).
- JWT storage/handling in `localStorage` (already built) is unaffected by
  the backend rewrite — RS256 vs HS256 is invisible to the frontend, it's
  still just "send the bearer token."

---

## 13. Open questions for you

Flagging these because I made a specific call in this doc but they're
genuinely your decisions:

1. **Deployment target** — Docker Compose is enough to develop and demo
   this. Do you actually need Kubernetes, or is that over-scoping for
   where this project is right now?
2. **RabbitMQ/Kafka timing** — proposed as phase 2+, only once
   charts-service needs it. Confirm you don't want event-driven
   architecture from day one.
3. **MinIO vs. just local disk** — MinIO is the "do it properly" choice,
   but if this stays single-instance/local-dev for a while, plain disk
   storage in file-service is simpler and you can swap later.
4. **Full GraphRAG vs. the lighter entity-graph approach in §4.1** — the
   full Microsoft-style community-report pipeline is significantly more
   work (and LLM cost) to build and keep current. Confirm the lighter
   version meets what you actually need "GraphRAG" for here.

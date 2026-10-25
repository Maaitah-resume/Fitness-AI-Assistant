# 🏋️ GymAI: Fitness AI Assistant

GymAI is an AI fitness coach. Users chat with a coach that knows their profile, training log, meals and
health notes, answers from their own documents with citations, searches the web when needed, and builds
downloadable workout and meal plans. The app tracks progress with charts, personal records, streaks and
badges, and sends a weekly AI report.

## ✨ Features

- **AI coach chat (Gemini):** remembers the user across all conversations, cites uploaded documents ([1], [2]) and web sources, and calls tools to update the profile, log weigh-ins and lifts, and generate PDF plans.
- **Progress tracking:** weigh-ins and lifts with charts, estimated one-rep max, personal-record detection and celebration.
- **Nutrition:** meal logging, including logging a meal from a photo (AI vision).
- **Weekly AI report:** a summary of the week with a PDF in Plans.
- **Achievements:** training and meal streaks, and badges whose rules admins define without code.
- **Profile:** health notes (injuries, allergies, diet) the coach always follows, a history timeline, and data export (JSON or PDF).
- **Plans:** every uploaded and generated document, with preview and download.
- **Settings:** language (English/Arabic, RTL), units, coach style, training preferences, appearance (theme style, light/dark, accent and text colours, text size), account management.
- **Admin portal:** overview dashboards, user management, coach prompt editing with version history, shared knowledge base, badge builder.
- **Themes:** Gym floor, Iron and Bloom colour themes, picked automatically from the profile's gender or chosen in Settings.

## 🧱 Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite, lucide-react icons, custom CSS design system |
| Backend | Java 21, Spring Boot 4 microservices, Spring Cloud Gateway, Spring Security (RS256 JWT) |
| AI | Spring AI with Google Gemini (chat, vision, embeddings); Gemini Google Search grounding with a DuckDuckGo fallback |
| Data | PostgreSQL, Redis, Qdrant (vectors), Neo4j (knowledge graph), MinIO (files) |
| Infra | Docker Compose |

🏗 Architecture (Spring Boot microservices)

The backend is seven Spring Boot services behind one API gateway. Each service owns its own data, and no service reads another's database. Services talk to each other over REST, and broadcast events through Redis.

```
                          React frontend (Vite :5173)
                                     │  /api/v1/**  (JWT bearer)
                                     ▼
                         ┌───────────────────────┐
                         │  api-gateway  :8080    │  routing · JWT check · revocation · rate limits
                         └───────────┬───────────┘
     ┌─────────────┬─────────────┬───┴─────────┬─────────────┐
     ▼             ▼             ▼             ▼             ▼
 user-service  profile-svc   chat-service  file-service  charts-service
   :8081         :8082         :8083         :8085         :8086
     │             │          │   │   │        │    │     (calls user, profile,
  Postgres      Postgres      │   │   │     Postgres MinIO  chat, file — cached
  user_db       profile_db    │   │   │     file_db          in Redis)
                              │   │   └──► file-service   (generate PDF)
                              │   └──────► profile-service (read/update profile)
                              ▼
                     knowledge-service :8084  ◄── file-service (index uploads)
                      Qdrant (vectors) + Neo4j (graph)

   Redis: rate limits · revoked tokens · chat memory window · charts cache · events
```

Only the gateway is exposed to the host. The services are reachable only inside the Docker network.

🧩 The services

| Service | Port | Responsibility | Data |
|---|---|---|---|
| **api-gateway** | 8080 | Single entry point. Routes `/api/v1/**` to the right service. Rejects invalid, expired or revoked tokens early. Rate-limits per user via Redis: 10 req/s by default, and a tighter bucket on `/chat/send`, since each message costs an LLM call. | Redis |
| **user-service** | 8081 | Accounts and auth: register, login, logout, `/auth/me`. Signs RS256 JWTs and publishes the public key at `/.well-known/jwks.json`. Admins list users, change roles and delete users here. | Postgres `user_db` |
| **profile-service** | 8082 | Fitness profile, preferences and health notes, progress log (weigh-ins, lifts), meals, achievements and admin-defined badges, profile history. | Postgres `profile_db` |
| **chat-service** | 8083 | The assistant. Runs each chat turn with citations and web search, keeps short- and long-term memory, analyses meal photos, writes weekly reports, and stores the admin-editable coach prompt. | Postgres `chat_db`, Redis |
| **knowledge-service** | 8084 | Retrieval (internal only, `/internal/knowledge/*`). Indexes document chunks into Qdrant (dense + sparse hybrid search), extracts entity triples like `(Squat)-[TARGETS]->(Quadriceps)` into Neo4j (GraphRAG), and stores per-user long-term memories. `/retrieve` merges all three. | Qdrant, Neo4j |
| **file-service** | 8085 | Upload: parse a PDF, chunk it, send it to knowledge-service for indexing. Generate: render a PDF, e.g. a workout plan from chat. Files live in MinIO; list, download, delete, plus admin views. | Postgres `file_db`, MinIO |
| **charts-service** | 8086 | Dashboards. `/charts/me` for a user's own stats, `/charts/admin/overview` and `/charts/admin/users` for admins. It has no database: it calls the other services' stats endpoints and caches results in Redis briefly. | Redis (cache) |

🔄 How the app interacts

**Login.** The frontend posts to `/api/v1/auth/login`, the gateway forwards it to user-service, and user-service returns a signed JWT with the user's id and role. The frontend stores it and sends it as `Authorization: Bearer …` on every call.

**Every request.** The gateway validates the token against user-service's JWKS and checks Redis for revoked tokens (logout). It applies the rate limit, then forwards the call. The receiving service validates the token again itself, so nothing relies on the gateway alone. Admin endpoints also require the `ADMIN` role.

**Sending a chat message (`POST /api/v1/chat/send`).**
1. chat-service loads the recent conversation window from Redis (or Postgres if it isn't cached).
2. It fetches the user's profile from profile-service.
3. It asks knowledge-service to `/retrieve` context: matching document chunks, related graph facts, and the user's long-term memories.
4. It calls the Gemini model with the system prompt, the user's data snapshot (profile, health notes, training log, recent meals, plans, latest weekly report, memories from all conversations; cached in Redis and refreshed every 4 messages), the retrieved context and the history. The model can call **tools** that act as the chatting user: update the profile, log weigh-ins and lifts (profile-service), generate a PDF document (file-service), or search the web.
5. The turn is saved to Postgres and the Redis window is updated. The reply returns, including links to any generated files.
6. Every 4th user message, a background step extracts durable facts ("trains at home", "knee injury") and saves them as long-term memory, which every later conversation sees.

If retrieval, profile or the LLM is down, the user still gets a reply (a fallback message), and their message is still saved.

**Uploading a document.** The frontend posts the PDF to `/api/v1/files/upload`. file-service stores it in MinIO, extracts and chunks the text, and sends the chunks to knowledge-service. knowledge-service embeds them into Qdrant and extracts graph triples into Neo4j. From then on, the content is available to chat retrieval.

**Dashboards.** charts-service calls the stats endpoints of user, profile, chat and file services, combines them, and caches the result in Redis.

**Deleting a user.** When an admin deletes a user, user-service publishes `events:user-deleted` on Redis. profile, chat, file and knowledge services each listen for it and delete their own data for that user.

🚀 Quick start

See **[docs/RUNNING.md](docs/RUNNING.md)** for the full guide. In short:

```bash
cp infra/.env.example infra/.env        # then set GEMINI_API_KEY
cd infra && docker compose up -d --build
cd ../frontend && npm install && npm run dev
```

Open **http://localhost:5173**. An admin account is created on first start (see RUNNING.md).

🗂 Project structure

```
backend/services/   api-gateway, user-, profile-, chat-, knowledge-, file-, charts-service (Spring Boot)
frontend/           React + Vite app (src/components, src/index.css design system, public/ photos)
infra/              docker-compose.yml, .env.example, Postgres init
docs/               running guide, architecture notes, reference/ (source PDFs)
```

🔐 Environment variables

Set these in `infra/.env` (never committed):

| Variable | Purpose |
|---|---|
| `GEMINI_API_KEY` | Google Gemini key. Without it the app runs, and chat replies with a fallback message. |
| `GEMINI_MODEL` | Gemini model name (default `gemini-3.5-flash`). |
| `WEB_SEARCH_ENABLED` | Lets the coach search the web (default `true`). |
| `SEED_ADMIN_USERNAME` / `SEED_ADMIN_PASSWORD` | The admin account created on first start. |

🛠 Troubleshooting

- **Chat says it hit the usage limit:** the Gemini free tier allows about 20 requests a day. Wait for the daily reset or use a key with billing.
- **A page shows "didn't load" after redeploying one service:** restart the gateway so it picks up the new container (`docker compose restart api-gateway`).
- **Web search says it's unavailable:** Google Search grounding needs billing; the coach falls back to DuckDuckGo automatically.

🏋️ Fitness AI Assistant

Fitness AI Assistant is a FastAPI-based conversational system that provides fitness guidance, muscle-gain advice, BMI-related insights, and general health support through a web-based chat interface.

The system combines:

Rule-aware conversation flow (profile building)

Generative AI responses (Gemini)

Persistent user profiles and chat history

A lightweight frontend served directly by the backend

✨ Key Features

💬 AI Fitness Chatbot
Natural language interaction for workouts, nutrition, and fitness questions.

👤 User Profile Creation
Collects age, weight, height, goal, and fitness level to personalize responses.

🧠 Hybrid Intelligence

Structured logic for profile flow

Generative AI for flexible, open-ended guidance

🗂 Conversation History Storage
All user and assistant messages are stored in SQLite for analysis and replay.

📊 Analysis Notebook
Jupyter notebooks for inspecting user profiles and chat history.

🌐 Single-Server Deployment
Backend and frontend are served together via FastAPI.

🧱 Tech Stack

Backend: Python, FastAPI, JWT auth (python-jose + bcrypt)

Frontend: React + Vite

Database: SQLite

AI Model: OpenAI (chat completions + embeddings), hybrid RAG via Qdrant (dense + TF-IDF sparse, RRF fusion)

Analysis: Pandas, Matplotlib, Seaborn, Jupyter

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
| **profile-service** | 8082 | Fitness profile: age, gender, height, weight, goal, level. `GET/PUT /profile/me`, plus admin views and stats. | Postgres `profile_db` |
| **chat-service** | 8083 | The assistant. Runs each chat turn, stores conversations and messages, and keeps short- and long-term memory. Admins can browse any user's chats and see stats. | Postgres `chat_db`, Redis |
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
4. It calls the OpenAI model with the system prompt, profile, context and history. The model can call **tools** that act as the chatting user: update the profile (profile-service) or generate a PDF document (file-service).
5. The turn is saved to Postgres and the Redis window is updated. The reply returns, including links to any generated files.
6. Every 6th user message, a background step extracts durable facts ("trains at home", "knee injury") and stores them in knowledge-service as long-term memory.

If retrieval, profile or the LLM is down, the user still gets a reply (a fallback message), and their message is still saved.

**Uploading a document.** The frontend posts the PDF to `/api/v1/files/upload`. file-service stores it in MinIO, extracts and chunks the text, and sends the chunks to knowledge-service. knowledge-service embeds them into Qdrant and extracts graph triples into Neo4j. From then on, the content is available to chat retrieval.

**Dashboards.** charts-service calls the stats endpoints of user, profile, chat and file services, combines them, and caches the result in Redis.

**Deleting a user.** When an admin deletes a user, user-service publishes `events:user-deleted` on Redis. profile, chat, file and knowledge services each listen for it and delete their own data for that user.

🚀 Quick Start

> The backend is now Spring Boot microservices. See **[docs/RUNNING.md](docs/RUNNING.md)** for how to run the app. The steps below are for the legacy Python backend.

1️⃣ Install dependencies (first time only)
pip install -r requirements.txt

2️⃣ Run the server
python run_server.py

3️⃣ Open the app in your browser

Main UI: http://localhost:8000

API Docs: http://localhost:8000/docs

Health Check: http://localhost:8000/health

🩺 Health Check

You can verify the server is running using:

curl http://localhost:8000/health


Expected response:

{ "status": "ok" }

🗂 Project Structure
FITNESS-AI-ASSISTANT/
├─ backend/
│  ├─ agents/            # Modular fitness tools
│  ├─ prompts/           # System + safety prompts
│  ├─ app.py             # FastAPI entrypoint
│  ├─ chat_logic.py      # Core conversation logic
│  ├─ db.py              # Database access layer
│  ├─ init_db.py         # Database initialization
│  └─ schema.sql         # SQLite schema
│
├─ frontend/
│  ├─ index.html
│  ├─ script.js
│  └─ style.css
│
├─ notebooks/
│  └─ analysis.ipynb     # Profile & chat analysis
│
├─ run_server.py
├─ requirements.txt
├─ README.md
├─ HOW_TO_RUN.md
├─ START_APP.md
└─ PROJECT_STATUS.md

🗃 Database Design (Overview)

The system uses SQLite with two core tables:

users

Stores user profile information:

username

age

gender

goal

fitness level

messages

Stores conversation history:

user / assistant role

message text

timestamp

user reference (foreign key)

This design enables:

Persistent chat history

Post-conversation analytics

Dashboard-style visualization

📊 Analysis & Visualization

The notebooks/analysis.ipynb notebook allows you to:

Load user profiles from SQLite

Inspect conversation history

Create bar charts and radar plots of user attributes

Analyze interaction patterns between user and assistant

This is useful for:

Debugging AI behavior

Academic reporting

UX evaluation

🔐 Environment Variables

Create a .env file (not committed to GitHub):

GEMINI_API_KEY=your_api_key_here


⚠️ .env and *.db files are excluded via .gitignore.

🛠 Troubleshooting
Site can’t be reached / connection refused

Ensure the server is running (python run_server.py)

Use localhost or 127.0.0.1, not 0.0.0.0

Make sure port 8000 is free

Disable VPNs or proxies blocking local traffic

Gemini API errors

Check API key validity

Verify quota limits

The app gracefully falls back with a user-friendly message

🔄 Development Workflow

To keep the repository clean:

Create a new branch from work

Make and test your changes locally

Commit with a clear message:

git commit -am "Improve profile flow handling"


Push and open a pull request

📌 Project Status

This project is:

✔ Fully functional

✔ Modular and extensible

✔ Suitable for academic submission or portfolio use

Future improvements may include:

User authentication

Multi-user sessions

Advanced analytics dashboard

💪 Final Note

Fitness AI Assistant demonstrates how structured logic and generative AI can work together to create reliable, user-friendly intelligent systems.
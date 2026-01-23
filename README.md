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

Backend: Python, FastAPI

Frontend: HTML, CSS, JavaScript

Database: SQLite

AI Model: Google Gemini API

Analysis: Pandas, Matplotlib, Seaborn, Jupyter

🚀 Quick Start
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
# Fitness AI Assistant

Fitness AI Assistant is a FastAPI-based chatbot with a web frontend for fitness guidance, BMI calculations, and general health tips.

## Quick Start
1. **Install dependencies** (first time only):
   ```bash
   pip install -r requirements.txt
   ```
2. **Run the server** (serves backend + frontend together):
   ```bash
   python run_server.py
   ```
3. **Open the app** in your browser at:
   - http://localhost:8000 (main UI)
   - http://localhost:8000/docs (API docs)
   - http://localhost:8000/health (health check)

## Troubleshooting "site can't be reached" / connection refused
- Make sure the server is running: after starting `python run_server.py` you should see `Uvicorn running on http://0.0.0.0:8000` in the terminal.
- Try the health check from the terminal:
  ```bash
  curl http://localhost:8000/health
  ```
  A healthy server returns `{ "status": "ok" }`.
- Use **localhost** or **127.0.0.1** in the browser address bar (avoid `0.0.0.0`).
- Ensure nothing else is using port **8000**. If it is, stop the other service or change the port in `run_server.py`.
- Disable VPNs/proxies that block local connections. On corporate networks, allow localhost traffic in your firewall/antivirus if prompted.
- If you stop the server with **Ctrl+C**, start it again before refreshing the page.

## Project Structure
- `backend/` – FastAPI app and routes.
- `frontend/` – Static assets served by the backend.
- `run_server.py` – Convenience entrypoint for local development.
- `data/`, `notebooks/` – Supporting datasets and experiments.

## Development workflow
If you make improvements locally, keep the history clean so others can follow along:

1. Create a new branch from `work` for your change.
2. Make and test your updates (preferably with `python run_server.py` running locally).
3. Commit the changes with a descriptive message (for example, `git commit -am "Update README quick start"`).
4. Push your branch and open a pull request so the updates are tracked in the repository.

Enjoy exploring the Fitness AI Assistant! 💪

## Additional documentation
- **HOW_TO_RUN.md** – Detailed setup, backend/ frontend start options, and troubleshooting tips.
- **START_APP.md** – Minimal quick-start commands for running the server.

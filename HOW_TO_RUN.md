# 🚀 How to Run Fitness AI Assistant

## Prerequisites
- Python 3.8+ installed
- Google Gemini API key in `.env` file

## Step 1: Install Dependencies

```bash
pip install -r requirements.txt
```

## Step 2: Verify API Key

Make sure your `.env` file contains:
```
GOOGLE_GEMINI_API_KEY=your_api_key_here
```

Test the API key:
```bash
python gemini_test.py
```

## Step 3: Start the Backend Server

### Option A: Using the run script (Recommended)
```bash
python run_server.py
```

### Option B: Using uvicorn directly
```bash
uvicorn backend.app:app --reload --host 0.0.0.0 --port 8000
```

The server will start at: **http://localhost:8000**

## Step 4: Open the Frontend

### Option A: Direct File (Simple)
1. Open `frontend/index.html` in your web browser
2. Double-click the file or right-click → Open with → Browser

### Option B: Using Python HTTP Server (Better for development)
```bash
cd frontend
python -m http.server 8080
```
Then open: **http://localhost:8080**

### Option C: Using Live Server (VS Code Extension)
1. Install "Live Server" extension in VS Code
2. Right-click `frontend/index.html`
3. Select "Open with Live Server"

## Step 5: Test the Application

1. **Check Backend Health:**
   - Visit: http://localhost:8000
   - Should show: `{"status":"running","service":"Fitness AI Assistant"}`

2. **Check API Docs:**
   - Visit: http://localhost:8000/docs
   - Interactive API documentation

3. **Test in Frontend:**
   - Try: "bmi 70 175"
   - Try: "What should I eat for breakfast?"
   - Try: "Suggest a workout plan for weight loss"

## Troubleshooting

### Backend not responding?
- Check if server is running on port 8000
- Verify API key is set correctly
- Check console for error messages

### CORS errors?
- Make sure CORS middleware is enabled (already added in app.py)
- Check browser console for specific errors

### Frontend can't connect?
- Verify backend is running: http://localhost:8000
- Check browser console (F12) for errors
- Make sure API_URL in `script.js` matches your backend URL

## Quick Test Commands

```bash
# Test backend directly
curl -X POST http://localhost:8000/chat -H "Content-Type: application/json" -d "{\"message\":\"Hello\"}"

# Test BMI calculation
curl -X POST http://localhost:8000/chat -H "Content-Type: application/json" -d "{\"message\":\"bmi 70 175\"}"
```

## What to Review

✅ **Backend:**
- FastAPI server running
- Gemini API integration working
- BMI tool functional
- Conversation logging active

✅ **Frontend:**
- Modern UI with gradient design
- Chat interface working
- Quick action buttons
- Loading states
- Error handling

✅ **Integration:**
- Frontend → Backend communication
- Real-time chat responses
- Message formatting

Enjoy your Fitness AI Assistant! 💪


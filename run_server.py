"""
Simple script to run the Fitness AI Assistant backend server.
"""
import uvicorn

if __name__ == "__main__":
    print("🚀 Starting Fitness AI Assistant Server...")
    print("\n" + "="*50)
    print("✅ Server is running!")
    print("="*50)
    print("\n📍 IMPORTANT: Use these URLs in your browser:")
    print("   🌐 Main App:    http://localhost:8000")
    print("   📚 API Docs:    http://localhost:8000/docs")
    print("   ❤️  Health:      http://localhost:8000/health")
    print("\n⚠️  NOTE: Do NOT use '0.0.0.0:8000' in browser!")
    print("   Use 'localhost:8000' or '127.0.0.1:8000' instead")
    print("\n" + "="*50)
    print("Press Ctrl+C to stop the server\n")
    
    uvicorn.run(
        "backend.app:app",
        host="0.0.0.0",
        port=8000,
        reload=True,  # Auto-reload on code changes
        log_level="info"
    )


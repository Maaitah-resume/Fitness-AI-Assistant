import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import logo from '../assets/logo.png';

function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();
        try {
            const response = await fetch('/api/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const result = await response.json();
            if (result.status === 'success') {
                localStorage.setItem('user_email', result.data.email || `${username}@example.com`);
                localStorage.setItem('user_id', result.data.user_id);
                localStorage.setItem('username', result.data.username);
                navigate('/chat');
            } else {
                alert(result.detail || 'Login failed');
            }
        } catch (error) {
            console.error('Login error:', error);
            alert('Login failed. Please try again.');
        }
    };

    return (
        <div className="login-page">
            <div className="login-card glass">
                <div className="login-header">
                    <img src={logo} alt="Fitness AI Logo" className="login-logo" />
                    <h1>GymAI</h1>
                    <p>Elevate your fitness journey</p>
                </div>

                <form onSubmit={handleLogin}>
                    <div className="form-group">
                        <label>Username</label>
                        <input
                            type="text"
                            placeholder="Enter your username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Password</label>
                        <input
                            type="password"
                            placeholder="••••••••"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                        />
                    </div>
                    <button type="submit" className="login-btn">
                        Sign In
                    </button>
                </form>

                <div className="login-footer">
                    <span>Don't have an account?</span>
                    <a href="/register">Sign Up</a>
                </div>
            </div>

            <style jsx>{`
        .login-page {
          height: 100vh;
          width: 100vw;
          display: flex;
          justify-content: center;
          align-items: center;
        }
        .login-card {
          width: 100%;
          max-width: 420px;
          padding: 3.5rem;
          border-radius: 32px;
          text-align: center;
          animation: fadeIn 0.8s ease-out;
        }
        .login-header {
          margin-bottom: 2.5rem;
        }
        .login-logo {
          width: 80px;
          height: 80px;
          margin-bottom: 1.5rem;
          border-radius: 20px;
          box-shadow: 0 10px 30px rgba(0,0,0,0.3);
        }
        .login-header h1 {
          font-size: 2.5rem;
          font-weight: 800;
          margin-bottom: 0.5rem;
          background: linear-gradient(135deg, #fff, var(--primary-light));
          -webkit-background-clip: text;
          background-clip: text;
          -webkit-text-fill-color: transparent;
        }
        .login-header p {
          color: var(--text-muted);
          font-size: 1rem;
        }
        .form-group {
          text-align: left;
          margin-bottom: 1.5rem;
        }
        .form-group label {
          display: block;
          font-size: 0.85rem;
          color: var(--text-muted);
          margin-bottom: 0.5rem;
          padding-left: 0.5rem;
        }
        .form-group input {
          width: 100%;
          padding: 1.1rem;
          border-radius: 16px;
          background: rgba(15, 23, 42, 0.5);
          border: 2px solid var(--glass-border);
          color: white;
          outline: none;
          transition: 0.3s;
        }
        .form-group input:focus {
          border-color: var(--primary);
          background: rgba(15, 23, 42, 0.7);
        }
        .login-btn {
          width: 100%;
          padding: 1.1rem;
          border-radius: 16px;
          background: var(--primary);
          border: none;
          color: white;
          font-weight: 700;
          font-size: 1rem;
          margin-top: 1rem;
          box-shadow: 0 10px 25px -5px rgba(99, 102, 241, 0.4);
        }
        .login-btn:hover {
          transform: translateY(-2px);
          filter: brightness(1.1);
        }
        .login-footer {
          margin-top: 2rem;
          font-size: 0.9rem;
          color: var(--text-muted);
          display: flex;
          justify-content: center;
          gap: 0.5rem;
        }
        .login-footer a {
          color: var(--primary-light);
          text-decoration: none;
          font-weight: 600;
        }
      `}</style>
        </div>
    );
}

export default Login;

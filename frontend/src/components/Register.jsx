import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import logo from '../assets/logo.png';

function Register() {
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate();

    const handleRegister = async (e) => {
        e.preventDefault();
        try {
            const response = await fetch('/api/v1/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, email })
            });
            const result = await response.json();
            if (result.status === 'success') {
                localStorage.setItem('user_email', email);
                localStorage.setItem('user_id', result.data.user_id);
                localStorage.setItem('username', username);
                navigate('/login');
            } else {
                alert(result.detail || 'Registration failed');
            }
        } catch (error) {
            console.error('Registration error:', error);
            alert('Registration failed. Please try again.');
        }
    };

    return (
        <div className="login-page">
            <div className="login-card glass">
                <div className="login-header">
                    <img src={logo} alt="Fitness AI Logo" className="login-logo" />
                    <h1>GymAI</h1>
                    <p>Create your fitness account</p>
                </div>

                <form onSubmit={handleRegister}>
                    <div className="form-group">
                        <label>Username</label>
                        <input
                            type="text"
                            placeholder="Pick a username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label>Email</label>
                        <input
                            type="email"
                            placeholder="your@email.com"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
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
                        Sign Up
                    </button>
                </form>

                <div className="login-footer">
                    <span>Already have an account?</span>
                    <a href="/login">Sign In</a>
                </div>
            </div>
        </div>
    );
}

export default Register;

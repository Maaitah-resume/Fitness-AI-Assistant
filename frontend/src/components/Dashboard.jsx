import React from 'react';
import { useNavigate } from 'react-router-dom';
import { MessageSquare, User, Dumbbell, Utensils } from 'lucide-react';

function Dashboard() {
    const navigate = useNavigate();

    const cards = [
        {
            title: 'Fitness Assistant',
            desc: 'Chat with GymAI about your workouts and nutrition.',
            icon: <MessageSquare size={32} />,
            path: '/chat',
            color: 'var(--primary)'
        },
        {
            title: 'My Profile',
            desc: 'Update your goals, age, and fitness level.',
            icon: <User size={32} />,
            path: '/profile',
            color: 'var(--accent)'
        },
        {
            title: 'Workout Plans',
            desc: 'View your personalized AI-generated routines.',
            icon: <Dumbbell size={32} />,
            path: '/workouts',
            color: '#10b981'
        },
        {
            title: 'Nutrition Tips',
            desc: 'Get healthy meal ideas tailored to your goals.',
            icon: <Utensils size={32} />,
            path: '/nutrition',
            color: '#f59e0b'
        }
    ];

    return (
        <div className="dashboard-container">
            <header className="dashboard-header">
                <h1>Welcome Back, Athlete</h1>
                <p>Your fitness journey is 85% complete today.</p>
            </header>

            <div className="dashboard-grid">
                {cards.map((card, i) => (
                    <div
                        key={i}
                        className="dashboard-card glass"
                        onClick={() => navigate(card.path)}
                    >
                        <div className="card-icon" style={{ color: card.color }}>
                            {card.icon}
                        </div>
                        <h3>{card.title}</h3>
                        <p>{card.desc}</p>
                        <div className="card-arrow">→</div>
                    </div>
                ))}
            </div>

            <style jsx>{`
        .dashboard-container {
          width: 100%;
          max-width: 1200px;
          padding: 4rem 2rem;
          margin: 0 auto;
        }
        .dashboard-header {
          margin-bottom: 4rem;
          text-align: center;
        }
        .dashboard-header h1 {
          font-size: 3rem;
          font-weight: 800;
          margin-bottom: 0.5rem;
          background: linear-gradient(135deg, #fff, var(--primary-light));
          -webkit-background-clip: text;
          background-clip: text;
          -webkit-text-fill-color: transparent;
        }
        .dashboard-header p {
          color: var(--text-muted);
          font-size: 1.1rem;
        }
        .dashboard-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
          gap: 2rem;
        }
        .dashboard-card {
          padding: 2.5rem;
          border-radius: 28px;
          cursor: pointer;
          position: relative;
          transition: 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
          display: flex;
          flex-direction: column;
          gap: 1rem;
        }
        .dashboard-card:hover {
          transform: translateY(-10px) scale(1.02);
          border-color: var(--primary);
          background: rgba(255, 255, 255, 0.08);
        }
        .card-icon {
          margin-bottom: 0.5rem;
        }
        .dashboard-card h3 {
          font-size: 1.5rem;
          font-weight: 700;
        }
        .dashboard-card p {
          color: var(--text-muted);
          font-size: 0.95rem;
          line-height: 1.6;
        }
        .card-arrow {
          position: absolute;
          bottom: 2rem;
          right: 2.5rem;
          font-size: 1.5rem;
          color: var(--primary-light);
          opacity: 0;
          transform: translateX(-10px);
          transition: 0.3s;
        }
        .dashboard-card:hover .card-arrow {
          opacity: 1;
          transform: translateX(0);
        }
      `}</style>
        </div>
    );
}

export default Dashboard;

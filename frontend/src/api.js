// Shared auth/session helpers + an authenticated fetch wrapper.

export function getToken() {
    return localStorage.getItem('token');
}

export function getUser() {
    return {
        token: localStorage.getItem('token'),
        userId: localStorage.getItem('user_id'),
        username: localStorage.getItem('username'),
        email: localStorage.getItem('user_email'),
        role: localStorage.getItem('role'),
    };
}

export function isAuthenticated() {
    return !!getToken();
}

export function isAdmin() {
    return localStorage.getItem('role') === 'admin';
}

export function storeSession({ access_token, user_id, username, email, role }) {
    localStorage.setItem('token', access_token);
    localStorage.setItem('user_id', user_id);
    localStorage.setItem('username', username);
    localStorage.setItem('user_email', email);
    localStorage.setItem('role', role);
}

export function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user_id');
    localStorage.removeItem('username');
    localStorage.removeItem('user_email');
    localStorage.removeItem('role');
}

// fetch() wrapper that attaches the bearer token and parses JSON.
// Throws on network failure; caller still checks result.status === 'success'.
export async function apiFetch(url, options = {}) {
    const token = getToken();
    const headers = { ...(options.headers || {}) };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(url, { ...options, headers });

    if (res.status === 401) {
        logout();
        window.location.href = '/login';
        throw new Error('Session expired. Please sign in again.');
    }

    return res.json();
}

import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import { LanguageProvider } from './i18n.jsx'
import { PreferencesProvider } from './prefs.jsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <LanguageProvider>
            <PreferencesProvider>
                <App />
            </PreferencesProvider>
        </LanguageProvider>
    </React.StrictMode>,
)

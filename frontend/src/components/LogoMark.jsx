import React from 'react';

// GymAI mark: a barbell loaded with competition plates (red 25, blue 20, yellow 15) on an iron tile.
// Same geometry as public/favicon.svg.
function LogoMark({ size = 72, className = '' }) {
    return (
        <svg className={className} width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="GymAI">
            <rect width="64" height="64" rx="6" fill="#2B1A17" />
            <rect x="5" y="30" width="54" height="4" rx="1" fill="#FBF1EA" />
            <rect x="11" y="15" width="6" height="34" rx="1.5" fill="#EE4B1F" />
            <rect x="18" y="19" width="5" height="26" rx="1.5" fill="#C8285A" />
            <rect x="24" y="23" width="4" height="18" rx="1.5" fill="#F5A623" />
            <rect x="47" y="15" width="6" height="34" rx="1.5" fill="#EE4B1F" />
            <rect x="41" y="19" width="5" height="26" rx="1.5" fill="#C8285A" />
            <rect x="36" y="23" width="4" height="18" rx="1.5" fill="#F5A623" />
        </svg>
    );
}

export default LogoMark;

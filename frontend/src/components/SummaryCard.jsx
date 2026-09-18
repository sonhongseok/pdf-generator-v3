// frontend/src/components/SummaryCard.jsx
import React from 'react';

export default function SummaryCard({ icon, label, value, unit, highlight = false }) {
    const cardClassName = highlight ? 'summary-card highlight' : 'summary-card';

    return (
        <div className={cardClassName}>
            <div className="card-icon">{icon}</div>
            <div className="card-value">
                {value.toLocaleString()}{unit}
            </div>
            <div className="card-label">{label}</div>
        </div>
    );
}

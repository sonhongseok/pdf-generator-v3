// frontend/src/components/HistorySearchBar.jsx
import React from 'react';

export default function HistorySearchBar({ searchTerm, onSearchChange }) {
    return (
        <div className="history-search-bar">
            <input
                type="text"
                placeholder="발급 번호 또는 시리얼 번호 검색..."
                value={searchTerm}
                onChange={(e) => onSearchChange(e.target.value)}
                className="history-search-input"
            />
        </div>
    );
}

// frontend/src/App.jsx
import React, { useState } from "react";
import "./App.css";
import CertificateForm from "./components/CertificateForm";
import CertificateHistoryList from "./components/CertificateHistoryList";
import Dashboard from "./components/Dashboard";

export default function App() {
  const [activeTab, setActiveTab] = useState("issue");

  return (
    <div className="window-frame">
      <div className="tab-container">
        <div
          className={`tab-btn ${activeTab === "issue" ? "active" : ""}`}
          onClick={() => setActiveTab("issue")}
        >
          성적서 발급
        </div>
        <div
          className={`tab-btn ${activeTab === "history" ? "active" : ""}`}
          onClick={() => setActiveTab("history")}
        >
          발급 이력 조회
        </div>
        {/* <div
          className={`tab-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => setActiveTab('dashboard')}
        >
          📊 현황 대시보드
        </div> */}
      </div>
      <div className="tab-content">
        {activeTab === "issue" && <CertificateForm />}
        {activeTab === "history" && <CertificateHistoryList />}
        {/* {activeTab === 'dashboard' && <Dashboard />} */}
      </div>
    </div>
  );
}

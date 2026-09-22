// src/components/Dashboard.jsx
import React, { useEffect, useState } from 'react';
import axios from 'axios';
import SummaryCard from './SummaryCard';
import DashboardMonthlyChart from './DashboardMonthlyChart';
import './Dashboard.css';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState(null);
  const [activeChart, setActiveChart] = useState('issuance');

  useEffect(() => {
    loadStats();
  }, []);

  async function loadStats() {
    try {
      setLoading(true);
      setErrorMessage(null);
      const response = await axios.get('/api/dashboard/stats');
      setStats(response.data);
    } catch (fetchError) {
      console.error(fetchError);
      setErrorMessage('통계 데이터를 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  if (loading) {
    return <div className="dashboard-status">통계 로딩 중...</div>;
  }

  if (errorMessage) {
    return <div className="dashboard-error">{errorMessage}</div>;
  }

  if (!stats) {
    return <div className="dashboard-status">표시할 통계 데이터가 없습니다.</div>;
  }

  const { summary, monthlyData } = stats;
  const hasData = monthlyData.some((item) => item.issuanceCount > 0);

  return (
    <div className="dashboard-container">
      <div className="summary-cards">
        <SummaryCard
          icon="📋"
          label="총 발급 건수"
          value={summary.totalIssuances}
          unit="건"
        />
        <SummaryCard
          icon="🔢"
          label="총 시리얼 수"
          value={summary.totalSerials}
          unit="개"
        />
        <SummaryCard
          icon="📅"
          label="이번달 발급"
          value={summary.thisMonthIssuances}
          unit="건"
          highlight={true}
        />
        <SummaryCard
          icon="📌"
          label="오늘 발급"
          value={summary.todayIssuances}
          unit="건"
        />
      </div>

      <DashboardMonthlyChart
        monthlyData={monthlyData}
        activeChart={activeChart}
        onToggleChart={setActiveChart}
        hasData={hasData}
      />

      <div className="dashboard-footer">
        <button className="refresh-btn" onClick={loadStats}>
          🔄 새로고침
        </button>
      </div>
    </div>
  );
}

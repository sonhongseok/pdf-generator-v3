// frontend/src/components/Dashboard.jsx
import React, { useEffect, useState } from 'react';
import axios from 'axios';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    Cell,
} from 'recharts';
import SummaryCard from './SummaryCard';
import './Dashboard.css';

const CHART_COLOR_ISSUANCE = '#3478c5';
const CHART_COLOR_SERIAL = '#27ae60';
const CHART_COLOR_INACTIVE = '#d1d5db';
const CHART_HEIGHT = 200;

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
        } catch (err) {
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

    const { summary, monthlyData } = stats;
    const hasData = monthlyData.some((item) => item.issuanceCount > 0);

    const activeColor = activeChart === 'issuance' ? CHART_COLOR_ISSUANCE : CHART_COLOR_SERIAL;
    const dataKey = activeChart === 'issuance' ? 'issuanceCount' : 'serialCount';

    const chartData = monthlyData.map((item) => ({
        label: item.label,
        issuanceCount: item.issuanceCount,
        serialCount: item.serialCount,
    }));

    const maxValue = Math.max(...chartData.map((item) => item[dataKey]), 0);

    return (
        <div className="dashboard-container">
            {/* 요약 카드 4종 */}
            <div className="summary-cards">
                <SummaryCard icon="📋" label="총 발급 건수" value={summary.totalIssuances} unit="건" />
                <SummaryCard icon="🔢" label="총 시리얼 수" value={summary.totalSerials} unit="개" />
                <SummaryCard icon="📅" label="이번달 발급" value={summary.thisMonthIssuances} unit="건" highlight={true} />
                <SummaryCard icon="📌" label="오늘 발급" value={summary.todayIssuances} unit="건" />
            </div>

            {/* 차트 영역 */}
            <div className="chart-section">
                <div className="chart-header">
                    <span className="chart-title">최근 6개월 발급 현황</span>
                    <div className="chart-toggle">
                        <button
                            className={`toggle-btn ${activeChart === 'issuance' ? 'active' : ''}`}
                            onClick={() => setActiveChart('issuance')}
                        >
                            발급 건수
                        </button>
                        <button
                            className={`toggle-btn ${activeChart === 'serial' ? 'active' : ''}`}
                            onClick={() => setActiveChart('serial')}
                        >
                            시리얼 수
                        </button>
                    </div>
                </div>

                {hasData ? (
                    <div className="chart-wrapper">
                        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
                            <BarChart
                                data={chartData}
                                margin={{ top: 16, right: 16, left: 0, bottom: 4 }}
                                barCategoryGap="30%"
                            >
                                <CartesianGrid
                                    strokeDasharray="3 3"
                                    stroke="#e0e0e0"
                                    vertical={false}
                                />
                                <XAxis
                                    dataKey="label"
                                    tick={{ fontSize: 11, fill: '#555', fontFamily: 'Tahoma, sans-serif' }}
                                    axisLine={{ stroke: '#cccccc' }}
                                    tickLine={false}
                                />
                                <YAxis
                                    allowDecimals={false}
                                    tick={{ fontSize: 10, fill: '#888', fontFamily: 'Tahoma, sans-serif' }}
                                    axisLine={false}
                                    tickLine={false}
                                    width={36}
                                    domain={[0, maxValue === 0 ? 1 : 'auto']}
                                />
                                <Tooltip
                                    cursor={{ fill: 'rgba(0, 0, 0, 0.04)' }}
                                    isAnimationActive={false}
                                    wrapperStyle={{ pointerEvents: 'none', outline: 'none' }}
                                    contentStyle={{
                                        fontSize: 12,
                                        fontFamily: 'Tahoma, sans-serif',
                                        border: '1px solid #cccccc',
                                        borderRadius: 4,
                                        padding: '6px 10px',
                                        backgroundColor: '#ffffff',
                                        boxShadow: '0 2px 6px rgba(0, 0, 0, 0.1)',
                                    }}
                                    formatter={(value) => [
                                        `${value.toLocaleString()}${activeChart === 'issuance' ? '건' : '개'}`,
                                        activeChart === 'issuance' ? '발급 건수' : '시리얼 수',
                                    ]}
                                    labelFormatter={(label) => `${label}`}
                                />
                                <Bar
                                    dataKey={dataKey}
                                    radius={[4, 4, 0, 0]}
                                    isAnimationActive={true}
                                    animationDuration={600}
                                    animationEasing="ease-out"
                                    label={{
                                        position: 'top',
                                        fontSize: 10,
                                        fill: '#333',
                                        fontFamily: 'Tahoma, sans-serif',
                                        fontWeight: 'bold',
                                        formatter: (v) => (v > 0 ? v : ''),
                                    }}
                                >
                                    {chartData.map((entry, index) => (
                                        <Cell
                                            key={`cell-${index}`}
                                            fill={entry[dataKey] > 0 ? activeColor : CHART_COLOR_INACTIVE}
                                        />
                                    ))}
                                </Bar>
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                ) : (
                    <div className="chart-empty">최근 6개월간 발급 이력이 없습니다.</div>
                )}
            </div>

            <div className="dashboard-footer">
                <button className="refresh-btn" onClick={loadStats}>
                    🔄 새로고침
                </button>
            </div>
        </div>
    );
}

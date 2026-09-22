// src/components/DashboardMonthlyChart.jsx
import React from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell
} from 'recharts';

const CHART_COLOR_ISSUANCE = '#3478c5';
const CHART_COLOR_SERIAL = '#27ae60';
const CHART_COLOR_INACTIVE = '#d1d5db';
const CHART_HEIGHT = 200;
const ANIMATION_DURATION_MS = 600;

export default function DashboardMonthlyChart({
  monthlyData,
  activeChart,
  onToggleChart,
  hasData
}) {
  const activeColor = activeChart === 'issuance' ? CHART_COLOR_ISSUANCE : CHART_COLOR_SERIAL;
  const dataKey = activeChart === 'issuance' ? 'issuanceCount' : 'serialCount';

  const chartData = monthlyData.map((item) => ({
    label: item.label,
    issuanceCount: item.issuanceCount,
    serialCount: item.serialCount
  }));

  const maxValue = Math.max(...chartData.map((item) => item[dataKey]), 0);

  return (
    <div className="chart-section">
      <div className="chart-header">
        <span className="chart-title">최근 6개월 발급 현황</span>
        <div className="chart-toggle">
          <button
            className={`toggle-btn ${activeChart === 'issuance' ? 'active' : ''}`}
            onClick={() => onToggleChart('issuance')}
          >
            발급 건수
          </button>
          <button
            className={`toggle-btn ${activeChart === 'serial' ? 'active' : ''}`}
            onClick={() => onToggleChart('serial')}
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
                tick={{
                  fontSize: 11,
                  fill: '#555',
                  fontFamily: 'Tahoma, sans-serif'
                }}
                axisLine={{ stroke: '#cccccc' }}
                tickLine={false}
              />
              <YAxis
                allowDecimals={false}
                tick={{
                  fontSize: 10,
                  fill: '#888',
                  fontFamily: 'Tahoma, sans-serif'
                }}
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
                  boxShadow: '0 2px 6px rgba(0, 0, 0, 0.1)'
                }}
                formatter={(value) => [
                  `${value.toLocaleString()}${activeChart === 'issuance' ? '건' : '개'}`,
                  activeChart === 'issuance' ? '발급 건수' : '시리얼 수'
                ]}
                labelFormatter={(label) => `${label}`}
              />
              <Bar
                dataKey={dataKey}
                radius={[4, 4, 0, 0]}
                isAnimationActive={true}
                animationDuration={ANIMATION_DURATION_MS}
                animationEasing="ease-out"
                label={{
                  position: 'top',
                  fontSize: 10,
                  fill: '#333',
                  fontFamily: 'Tahoma, sans-serif',
                  fontWeight: 'bold',
                  formatter: (val) => (val > 0 ? val : '')
                }}
              >
                {chartData.map((entry, index) => {
                  const barFill = entry[dataKey] > 0 ? activeColor : CHART_COLOR_INACTIVE;
                  return <Cell key={`cell-${index}`} fill={barFill} />;
                })}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="chart-empty">최근 6개월간 발급 이력이 없습니다.</div>
      )}
    </div>
  );
}

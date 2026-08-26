import { useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useMetrics, useRecommendations } from '../hooks/useApi';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  AreaChart, Area, ReferenceLine
} from 'recharts';
import { format } from 'date-fns';

const TIME_RANGES = [
  { label: '24h', days: 1 },
  { label: '1w', days: 7 },
  { label: '4w', days: 28 },
  { label: '1m', days: 30 },
  { label: '3m', days: 90 },
];

const RANGE_LABELS: Record<number, string> = {
  1: '24 hours',
  7: '1 week',
  28: '4 weeks',
  30: '1 month',
  90: '3 months',
};

const tooltipStyle = {
  backgroundColor: 'var(--bg-card)',
  border: '1px solid var(--border)',
  borderRadius: '6px',
  color: 'var(--text-primary)',
};

function CustomTick({ x, y, payload }: any) {
  const label = payload.value;
  const parts = label.split('|');
  const isMiddle = parts.length > 1;

  return (
    <g transform={`translate(${x},${y})`}>
      {isMiddle ? (
        <>
          <text x={0} y={0} dy={16} textAnchor="middle" fill="var(--text-muted)" fontSize={11}>
            {parts[1]}
          </text>
          <text x={0} y={0} dy={28} textAnchor="middle" fill="var(--text-muted)" fontSize={9}>
            {parts[0]}
          </text>
        </>
      ) : (
        <text x={0} y={0} dy={16} textAnchor="middle" fill="var(--text-muted)" fontSize={11}>
          {parts[0]}
        </text>
      )}
    </g>
  );
}

export default function ResourceDetailPage() {
  const { resourceId } = useParams<{ resourceId: string }>();
  const [selectedDays, setSelectedDays] = useState(30);
  const { metrics, loading: metricsLoading } = useMetrics(resourceId ?? null, selectedDays);
  const { recommendations } = useRecommendations(resourceId ?? null, selectedDays);

  const rangeLabel = RANGE_LABELS[selectedDays] || `${selectedDays} days`;

  const chartData = useMemo(() => {
    if (!metrics) return [];
    const points = metrics.dataPoints.map((p) => ({
      timestamp: p.timestamp,
      cpu: Number(p.cpuUtilization.toFixed(2)),
      memory: Number(p.memoryUtilization.toFixed(2)),
      requests: p.activeRequestCount,
    }));

    const dayGroups: Record<string, number[]> = {};
    points.forEach((p, i) => {
      const day = format(new Date(p.timestamp), 'yyyy-MM-dd');
      if (!dayGroups[day]) dayGroups[day] = [];
      dayGroups[day].push(i);
    });

    return points.map((p, i) => {
      const day = format(new Date(p.timestamp), 'yyyy-MM-dd');
      const indices = dayGroups[day];
      const middleIndex = indices[Math.floor(indices.length / 2)];
      const time = format(new Date(p.timestamp), 'HH:mm');
      const date = format(new Date(p.timestamp), 'MMM dd');

      return {
        ...p,
        tickLabel: i === middleIndex ? `${time}|${date}` : time,
      };
    });
  }, [metrics]);

  if (metricsLoading && !metrics) return <div className="loading">Loading metrics...</div>;
  if (!metrics) return <div className="error">Resource not found</div>;

  return (
    <div className="page">
      <Link to="/" className="back-link">&larr; Back to Dashboard</Link>
      <h1>{metrics.resourceName}</h1>
      <p className="subtitle">{metrics.resourceId} &mdash; {metrics.resourceType}</p>

      <div className="time-range-selector">
        {TIME_RANGES.map((range) => (
          <button
            key={range.days}
            className={`time-range-btn ${selectedDays === range.days ? 'active' : ''}`}
            onClick={() => setSelectedDays(range.days)}
          >
            {range.label}
          </button>
        ))}
      </div>

      <div className="stats-row">
        <div className="stat-box">
          <span className="stat-label">Avg CPU</span>
          <span className="stat-value">{metrics.stats.avgCpuUtilization.toFixed(1)}%</span>
        </div>
        <div className="stat-box">
          <span className="stat-label">Peak CPU</span>
          <span className="stat-value">{metrics.stats.peakHourUtilization.toFixed(1)}%</span>
        </div>
        <div className="stat-box">
          <span className="stat-label">Off-Peak CPU</span>
          <span className="stat-value">{metrics.stats.offPeakHourUtilization.toFixed(1)}%</span>
        </div>
        <div className="stat-box">
          <span className="stat-label">Avg Memory</span>
          <span className="stat-value">{metrics.stats.avgMemoryUtilization.toFixed(1)}%</span>
        </div>
      </div>

      <div className="chart-section">
        <h2>CPU Utilization ({rangeLabel})</h2>
        <ResponsiveContainer width="100%" height={300}>
          <AreaChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="tickLabel" tick={<CustomTick />} />
            <YAxis domain={[0, 100]} />
            <Tooltip contentStyle={tooltipStyle} />
            <ReferenceLine y={65} stroke="var(--warning)" strokeDasharray="3 3" label="Peak Target" />
            <ReferenceLine y={10} stroke="var(--success)" strokeDasharray="3 3" label="Off-Peak Target" />
            <Area type="monotone" dataKey="cpu" stroke="var(--cpu-color)" fill="var(--cpu-color)" fillOpacity={0.3} />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      <div className="chart-section">
        <h2>Memory Utilization ({rangeLabel})</h2>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="tickLabel" tick={<CustomTick />} />
            <YAxis domain={[0, 100]} />
            <Tooltip contentStyle={tooltipStyle} />
            <Legend />
            <Line type="monotone" dataKey="memory" stroke="var(--memory-color)" name="Memory %" />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {recommendations.length > 0 && (
        <div className="recommendations-section">
          <h2>Optimization Recommendations</h2>
          {recommendations.map((rec, i) => (
            <div key={i} className="card recommendation-card">
              <div className="rec-header">
                <h3>{rec.recommendationType.replace(/_/g, ' ')}</h3>
                <span className="savings-badge">Save ${rec.estimatedMonthlySavingsUsd.toFixed(0)}/mo ({rec.estimatedSavingsPercentage.toFixed(1)}%)</span>
              </div>
              <div className="rec-body">
                <div className="rec-configs">
                  <div>
                    <h4>Current</h4>
                    <pre>{rec.currentConfiguration}</pre>
                  </div>
                  <div>
                    <h4>Recommended</h4>
                    <pre>{rec.recommendedConfiguration}</pre>
                  </div>
                </div>
                <p className="rec-rationale">{rec.rationale}</p>
                <div className="rec-meta">
                  <span>Confidence: {(rec.confidenceScore * 100).toFixed(0)}%</span>
                  <span>Peak: {rec.peakSchedule}</span>
                </div>
              </div>
            </div>
          ))}
          <Link to={`/resources/${resourceId}/generate`} className="btn btn-primary">
            Generate Scaling Code &rarr;
          </Link>
        </div>
      )}
    </div>
  );
}

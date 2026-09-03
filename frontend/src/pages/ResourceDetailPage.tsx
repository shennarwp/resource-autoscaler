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
  fontSize: '0.75rem',
  padding: '8px 12px',
};

function ChartTooltip({ active, label, payload }: any) {
  if (!active || !payload?.length) return null;
  const parts = (label as string).split('|');
  return (
    <div style={tooltipStyle} className="chart-tooltip">
      <p style={{ fontWeight: 600, marginBottom: 4 }}>{parts[0]}</p>
      {parts.length > 1 && <p style={{ color: 'var(--text-muted)', marginBottom: 4 }}>{parts[1]}</p>}
      {payload.map((entry: any, i: number) => (
        <p key={i} style={{ color: entry.color }}>
          {entry.name}: {entry.value}%
        </p>
      ))}
    </div>
  );
}

function CustomTick({ x, y, payload }: any) {
  const label = payload.value;
  const parts = label.split('|');

  return (
    <g transform={`translate(${x},${y})`}>
      <text x={0} y={0} dy={14} textAnchor="middle" fill="var(--text-muted)" fontSize={9}>
        {parts[0]}
      </text>
      {parts.length > 1 && (
        <text x={0} y={0} dy={24} textAnchor="middle" fill="var(--text-muted)" fontSize={8}>
          {parts[1]}
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

  const tickInterval = selectedDays <= 1 ? 0
    : selectedDays <= 7 ? 6
    : selectedDays <= 30 ? 71
    : 119;

  const chartData = useMemo(() => {
    if (!metrics) return [];
    const points = metrics.dataPoints.map((p) => ({
      timestamp: p.timestamp,
      cpu: Number(p.cpuUtilization.toFixed(2)),
      memory: Number(p.memoryUtilization.toFixed(2)),
      requests: p.activeRequestCount,
    }));

    return points.map((p) => {
      const ts = new Date(p.timestamp);
      const time = format(ts, 'HH:mm');
      const date = format(ts, 'MMM dd');

      return {
        ...p,
        tickLabel: `${time}|${date}`,
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
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="tickLabel" tick={<CustomTick />} interval={tickInterval} />
            <YAxis domain={[0, 100]} />
            <Tooltip content={<ChartTooltip />} />
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
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="tickLabel" tick={<CustomTick />} interval={tickInterval} />
            <YAxis domain={[0, 100]} />
            <Tooltip content={<ChartTooltip />} />
            <Legend />
            <Line type="monotone" dataKey="memory" stroke="var(--memory-color)" name="Memory %" />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="recommendations-section">
          <h2>Optimization Recommendations</h2>
          {recommendations.length > 0 ? (
            <>
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
            </>
          ) : (
            <p className="empty-state">No optimization recommendations for this resource</p>
          )}
        </div>
    </div>
  );
}

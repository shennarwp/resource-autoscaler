import { useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useMetrics, useRecommendations } from '../hooks/useApi';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  AreaChart, Area, ReferenceLine
} from 'recharts';
import { format } from 'date-fns';

const TIME_RANGES = [
  { label: '5m', days: 5 / 1440 },
  { label: '15m', days: 15 / 1440 },
  { label: '30m', days: 30 / 1440 },
  { label: '3h', days: 0.125 },
  { label: '6h', days: 0.25 },
  { label: '12h', days: 0.5 },
  { label: '24h', days: 1 },
  { label: '1w', days: 7 },
  { label: '1m', days: 30 },
  { label: '3m', days: 90 },
];

const RANGE_LABELS: Record<number, string> = {
  [5 / 1440]: '5 minutes',
  [15 / 1440]: '15 minutes',
  [30 / 1440]: '30 minutes',
  0.125: '3 hours',
  0.25: '6 hours',
  0.5: '12 hours',
  1: '24 hours',
  7: '1 week',
  30: '1 month',
  90: '3 months',
};

// How often to render an x-axis tick label, in minutes, per time range (in days).
const LABEL_EVERY_MINUTES: Record<number, number> = {
  [5 / 1440]: 1,
  [15 / 1440]: 1,
  [30 / 1440]: 3,
  0.125: 30,
  0.25: 60,
  0.5: 60,
  1: 60,
  7: 12 * 60,
  30: 24 * 60,
  90: 5 * 24 * 60,
};

const tooltipStyle = {
  backgroundColor: 'var(--bg-card)',
  border: '1px solid var(--border)',
  borderRadius: '6px',
  color: 'var(--text-primary)',
  fontSize: '0.75rem',
  padding: '8px 12px',
};

function ChartTooltip({ active, payload }: any) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as DataPoint;
  return (
    <div style={tooltipStyle} className="chart-tooltip">
      <p style={{ fontWeight: 600, marginBottom: 4 }}>{format(point.t, 'HH:mm')}</p>
      <p style={{ color: 'var(--text-muted)', marginBottom: 4 }}>{format(point.t, 'MMM dd')}</p>
      {payload.map((entry: any, i: number) => (
        <p key={i} style={{ color: entry.color }}>
          {entry.name}: {entry.value}%
        </p>
      ))}
    </div>
  );
}

interface DataPoint {
  t: number;
  cpu: number;
  memory: number;
  requests: number;
}

function computeLabelTimes(minT: number, maxT: number, everyMinutes: number): number[] {
  const step = everyMinutes * 60 * 1000;
  const times: number[] = [];
  for (let t = Math.floor(minT / step) * step; t <= maxT; t += step) {
    times.push(t);
  }
  return times;
}

function tickFormatter(value: number): string {
  const date = new Date(value);
  const time = format(date, 'HH:mm');
  const dateLabel = format(date, 'MMM dd');
  return `${time}|${dateLabel}`;
}

export default function ResourceDetailPage() {
  const { resourceId } = useParams<{ resourceId: string }>();
  const [selectedDays, setSelectedDays] = useState(30);
  const { metrics, loading: metricsLoading } = useMetrics(resourceId ?? null, selectedDays);
  const { recommendations } = useRecommendations(resourceId ?? null, selectedDays);

  const rangeLabel = RANGE_LABELS[selectedDays] || `${selectedDays} days`;
  const labelEvery = LABEL_EVERY_MINUTES[selectedDays] ?? 60;

  const chartData = useMemo(() => {
    if (!metrics) return [];
    const points = metrics.dataPoints
      .map((p) => ({
        t: new Date(p.timestamp).getTime(),
        cpu: Number(p.cpuUtilization.toFixed(2)),
        memory: Number(p.memoryUtilization.toFixed(2)),
        requests: p.activeRequestCount,
      }))
      .sort((a, b) => a.t - b.t);

    return points;
  }, [metrics]);

  const labelTimes = useMemo(() => {
    if (chartData.length === 0) return [];
    const minT = chartData[0].t;
    const maxT = chartData[chartData.length - 1].t;
    return computeLabelTimes(minT, maxT, labelEvery);
  }, [chartData, labelEvery]);

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
            <XAxis
              dataKey="t"
              type="number"
              scale="time"
              domain={['dataMin', 'dataMax']}
              ticks={labelTimes}
              tickFormatter={tickFormatter}
              tick={{ fill: 'var(--text-muted)', fontSize: 9 }}
            />
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
            <XAxis
              dataKey="t"
              type="number"
              scale="time"
              domain={['dataMin', 'dataMax']}
              ticks={labelTimes}
              tickFormatter={tickFormatter}
              tick={{ fill: 'var(--text-muted)', fontSize: 9 }}
            />
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

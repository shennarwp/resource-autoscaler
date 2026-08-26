import { useParams, Link } from 'react-router-dom';
import { useMetrics, useRecommendations } from '../hooks/useApi';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  AreaChart, Area, ReferenceLine
} from 'recharts';
import { format } from 'date-fns';

export default function ResourceDetailPage() {
  const { resourceId } = useParams<{ resourceId: string }>();
  const { metrics, loading: metricsLoading } = useMetrics(resourceId ?? null);
  const { recommendations, loading: recsLoading } = useRecommendations(resourceId ?? null);

  if (metricsLoading || recsLoading) return <div className="loading">Loading metrics...</div>;
  if (!metrics) return <div className="error">Resource not found</div>;

  const chartData = metrics.dataPoints.map((p) => ({
    time: format(new Date(p.timestamp), 'MMM dd HH:mm'),
    cpu: Number(p.cpuUtilization.toFixed(2)),
    memory: Number(p.memoryUtilization.toFixed(2)),
    requests: p.activeRequestCount,
  }));

  return (
    <div className="page">
      <Link to="/" className="back-link">&larr; Back to Dashboard</Link>
      <h1>{metrics.resourceName}</h1>
      <p className="subtitle">{metrics.resourceId} &mdash; {metrics.resourceType}</p>

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
        <h2>CPU Utilization (30 days)</h2>
        <ResponsiveContainer width="100%" height={300}>
          <AreaChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="time" tick={{ fontSize: 11 }} />
            <YAxis domain={[0, 100]} />
            <Tooltip />
            <ReferenceLine y={65} stroke="#f59e0b" strokeDasharray="3 3" label="Peak Target" />
            <ReferenceLine y={10} stroke="#10b981" strokeDasharray="3 3" label="Off-Peak Target" />
            <Area type="monotone" dataKey="cpu" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.3} />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      <div className="chart-section">
        <h2>Memory Utilization (30 days)</h2>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="time" tick={{ fontSize: 11 }} />
            <YAxis domain={[0, 100]} />
            <Tooltip />
            <Legend />
            <Line type="monotone" dataKey="memory" stroke="#8b5cf6" name="Memory %" />
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

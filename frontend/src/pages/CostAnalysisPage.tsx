import { Link } from 'react-router-dom';
import { useCostAnalysis } from '../hooks/useApi';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import Skeleton, { SkeletonChart } from '../components/Skeleton';

/** Shared chart tooltip styling for the dark cost-analysis theme. */
const tooltipStyle = {
  backgroundColor: 'var(--bg-card)',
  border: '1px solid var(--border)',
  borderRadius: '6px',
  color: 'var(--text-primary)',
};

/** Displays current versus optimized cost by resource. */
export default function CostAnalysisPage() {
  const { analysis, loading, error } = useCostAnalysis();

  if (loading) {
    return (
      <div className="page">
        <Skeleton width="160px" height="1rem" />
        <Skeleton width="250px" height="1.75rem" />
        <SkeletonChart />
        <Skeleton width="100%" height="200px" />
      </div>
    );
  }
  if (error) return <div className="error" role="alert">{error}</div>;
  if (!analysis || !analysis.resources?.length) {
    return <div className="empty-state">No resources to analyze.</div>;
  }

  const chartData = analysis.resources.map((r) => ({
    name: r.resourceName,
    current: r.currentMonthlyCostUsd,
    optimized: r.optimizedMonthlyCostUsd,
    savings: r.potentialSavingsUsd,
  }));

  return (
    <div className="page">
      <Link to="/" className="back-link">&larr; Back to Dashboard</Link>
      <h1>Cost Analysis</h1>
      <p className="subtitle">Detailed breakdown of current vs optimized spending</p>

      <div className="chart-section">
        <h2 id="cost-chart-title">Cost Comparison by Resource</h2>
        <div className="chart-canvas" role="img" aria-labelledby="cost-chart-title">
          <ResponsiveContainer width="100%" height={400}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip contentStyle={tooltipStyle} formatter={(value) => `$${Number(value).toFixed(2)}`} />
              <Bar dataKey="current" fill="#ef4444" name="Current ($)" />
              <Bar dataKey="optimized" fill="#10b981" name="Optimized ($)" />
            </BarChart>
          </ResponsiveContainer>
        </div>
        <p className="visually-hidden">
          Total current spend: ${analysis.summary?.totalCurrentCostUsd?.toFixed(2) ?? '0'} per month.
          Optimized spend: ${analysis.summary?.totalOptimizedCostUsd?.toFixed(2) ?? '0'}.
          Potential savings: ${analysis.summary?.totalPotentialSavingsUsd?.toFixed(2) ?? '0'} per month.
        </p>
      </div>

      <h2>Resource Breakdown</h2>
      <div className="cost-table">
        <table>
          <caption className="visually-hidden">Per-resource cost comparison with current, optimized, and savings values</caption>
          <thead>
            <tr>
              <th scope="col">Resource</th>
              <th scope="col">Type</th>
              <th scope="col">Current</th>
              <th scope="col">Optimized</th>
              <th scope="col">Savings</th>
              <th scope="col">Peak CPU</th>
              <th scope="col">Off-Peak CPU</th>
              <th scope="col" aria-label="Details"></th>
            </tr>
          </thead>
          <tbody>
            {analysis.resources.map((r) => (
              <tr key={r.resourceId}>
                <td>{r.resourceName}</td>
                <td>{r.resourceType}</td>
                <td>${r.currentMonthlyCostUsd.toFixed(2)}</td>
                <td>${r.optimizedMonthlyCostUsd.toFixed(2)}</td>
                <td className="savings-cell">
                  ${r.potentialSavingsUsd.toFixed(2)} ({r.savingsPercentage.toFixed(1)}%)
                </td>
                <td>{r.currentPeakCpuPercent.toFixed(1)}%</td>
                <td>{r.currentOffPeakCpuPercent.toFixed(1)}%</td>
                <td>
                  <Link to={`/resources/${r.resourceId}`} className="btn btn-sm">
                    Details
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

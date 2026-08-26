import { Link } from 'react-router-dom';
import { useCostAnalysis } from '../hooks/useApi';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';

export default function CostAnalysisPage() {
  const { analysis, loading, error } = useCostAnalysis();

  if (loading) return <div className="loading">Analyzing costs...</div>;
  if (error) return <div className="error">{error}</div>;
  if (!analysis) return null;

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
        <h2>Cost Comparison by Resource</h2>
        <ResponsiveContainer width="100%" height={400}>
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis />
            <Tooltip formatter={(value: number) => `$${value.toFixed(2)}`} />
            <Bar dataKey="current" fill="#ef4444" name="Current ($)" />
            <Bar dataKey="optimized" fill="#10b981" name="Optimized ($)" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <h2>Resource Breakdown</h2>
      <div className="cost-table">
        <table>
          <thead>
            <tr>
              <th>Resource</th>
              <th>Type</th>
              <th>Current</th>
              <th>Optimized</th>
              <th>Savings</th>
              <th>Peak CPU</th>
              <th>Off-Peak CPU</th>
              <th></th>
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

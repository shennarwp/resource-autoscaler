import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { metricsApi } from '../services/api';
import { useCostAnalysis } from '../hooks/useApi';

export default function DashboardPage() {
  const [resources, setResources] = useState<string[]>([]);
  const { analysis, loading: costLoading } = useCostAnalysis();

  useEffect(() => {
    metricsApi.getMonitoredResources().then(setResources);
  }, []);

  const summary = analysis?.summary;

  return (
    <div className="page">
      <h1>Cloud Cost & FinOps Dashboard</h1>
      <p className="subtitle">Autonomous infrastructure cost optimization platform</p>

      {summary && (
        <div className="summary-cards">
          <div className="card summary-card">
            <h3>Monthly Spend</h3>
            <p className="metric-value">${summary.totalCurrentCostUsd.toLocaleString()}</p>
            <span className="metric-label">Current</span>
          </div>
          <div className="card summary-card optimized">
            <h3>Optimized Spend</h3>
            <p className="metric-value">${summary.totalOptimizedCostUsd.toLocaleString()}</p>
            <span className="metric-label">After optimization</span>
          </div>
          <div className="card summary-card savings">
            <h3>Potential Savings</h3>
            <p className="metric-value">${summary.totalPotentialSavingsUsd.toLocaleString()}</p>
            <span className="metric-label">{summary.overallSavingsPercentage.toFixed(1)}% reduction</span>
          </div>
          <div className="card summary-card annual">
            <h3>Annual Savings</h3>
            <p className="metric-value">${summary.estimatedAnnualSavingsUsd}</p>
            <span className="metric-label">Projected yearly</span>
          </div>
        </div>
      )}

      <h2>Monitored Resources</h2>
      <div className="resource-grid">
        {resources.map((resourceId) => (
          <Link key={resourceId} to={`/resources/${resourceId}`} className="card resource-card">
            <h3>{resourceId}</h3>
            <span className="card-arrow">&rarr;</span>
          </Link>
        ))}
        {resources.length === 0 && !costLoading && (
          <p className="empty-state">No resources being monitored. Configure Azure connection or run with mock profile.</p>
        )}
      </div>
    </div>
  );
}

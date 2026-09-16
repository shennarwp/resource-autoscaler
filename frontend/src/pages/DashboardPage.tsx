import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { metricsApi } from '../services/api';
import { useCostAnalysis } from '../hooks/useApi';
import { SkeletonCard } from '../components/Skeleton';

/** Shows monitored resources and the aggregate cost summary. */
export default function DashboardPage() {
  const [resources, setResources] = useState<string[]>([]);
  const [resourcesLoading, setResourcesLoading] = useState(true);
  const [resourcesError, setResourcesError] = useState<string | null>(null);
  const [resourcesVersion, setResourcesVersion] = useState(0);
  const { analysis, loading: costLoading, error: costError, reload } = useCostAnalysis();

  useEffect(() => {
    const controller = new AbortController();
    metricsApi
      .getMonitoredResources(controller.signal)
      .then(setResources)
      .catch((err) => {
        if (!controller.signal.aborted) setResourcesError(err.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setResourcesLoading(false);
      });
    return () => controller.abort();
  }, [resourcesVersion]);

  const refresh = () => {
    setResourcesLoading(true);
    setResourcesError(null);
    reload?.();
    setResourcesVersion((v) => v + 1);
  };

  const summary = analysis?.summary;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Cloud Cost & FinOps Dashboard</h1>
          <p className="subtitle">Autonomous infrastructure cost optimization platform</p>
        </div>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={refresh}
          aria-label="Refresh dashboard data"
        >
          Refresh
        </button>
      </div>

      {costLoading && !summary && (
        <div className="summary-cards">
          {Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)}
        </div>
      )}

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

      {costError && <div className="error" role="alert" style={{ marginBottom: '1rem' }}>Failed to load cost data: {costError}</div>}
      <h2>Monitored Resources</h2>
      {resourcesError && <div className="error" role="alert">Failed to load monitored resources: {resourcesError}</div>}
      <div className="resource-grid" aria-label="Monitored resources">
        {resourcesLoading && resources.length === 0 && (
          Array.from({ length: 3 }).map((_, i) => <SkeletonCard key={i} />)
        )}
        {resources.map((resourceId) => (
          <Link key={resourceId} to={`/resources/${resourceId}`} className="card resource-card">
            <h3>{resourceId}</h3>
            <span className="card-arrow">&rarr;</span>
          </Link>
        ))}
        {resources.length === 0 && !resourcesLoading && !resourcesError && (
          <p className="empty-state">No resources being monitored. Configure Azure connection or run with mock profile.</p>
        )}
      </div>
    </div>
  );
}
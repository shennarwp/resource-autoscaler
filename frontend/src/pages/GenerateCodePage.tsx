import { useParams, Link } from 'react-router-dom';
import { useState } from 'react';
import { recommendationsApi } from '../services/api';
import type { RecommendationResponse } from '../types/api';

export default function GenerateCodePage() {
  const { resourceId } = useParams<{ resourceId: string }>();
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'keda' | 'terraform'>('keda');

  const handleGenerate = async () => {
    if (!resourceId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await recommendationsApi.generateCode(resourceId);
      setResult(res);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to generate code');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <Link to={`/resources/${resourceId}`} className="back-link">&larr; Back to Resource</Link>
      <h1>Generate Scaling Code</h1>
      <p className="subtitle">Auto-generate KEDA ScaledObject or Terraform autoscale configuration</p>

      {!result && (
        <button className="btn btn-primary" onClick={handleGenerate} disabled={loading}>
          {loading ? 'Generating...' : 'Generate Code'}
        </button>
      )}

      {error && <div className="error">{error}</div>}

      {result && (
        <div className="code-generation-result">
          <div className="rec-summary card">
            <h3>{result.recommendation.resourceName}</h3>
            <p>Type: {result.recommendation.recommendationType.replace(/_/g, ' ')}</p>
            <p>Est. savings: ${result.recommendation.estimatedMonthlySavingsUsd.toFixed(0)}/mo</p>
          </div>

          <div className="code-tabs">
            <button
              className={`tab ${activeTab === 'keda' ? 'active' : ''}`}
              onClick={() => setActiveTab('keda')}
            >
              KEDA ScaledObject (AKS)
            </button>
            <button
              className={`tab ${activeTab === 'terraform' ? 'active' : ''}`}
              onClick={() => setActiveTab('terraform')}
            >
              Terraform (Azure VM/App Service)
            </button>
          </div>

          <div className="code-block">
            <pre>
              <code>
                {activeTab === 'keda' ? result.kedaYaml : result.terraformHcl}
              </code>
            </pre>
            <button
              className="btn btn-copy"
              onClick={() => {
                navigator.clipboard.writeText(activeTab === 'keda' ? result.kedaYaml : result.terraformHcl);
              }}
            >
              Copy to Clipboard
            </button>
          </div>

          <div className="actions-row">
            <button className="btn btn-secondary" onClick={() => setResult(null)}>
              Regenerate
            </button>
            <Link to={`/resources/${resourceId}`} className="btn btn-secondary">
              View Metrics
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

import { useParams, Link } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { recommendationsApi } from '../services/api';
import type { RecommendationResponse } from '../types/api';

export default function GenerateCodePage() {
  const { resourceId } = useParams<{ resourceId: string }>();
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [noRecommendation, setNoRecommendation] = useState(false);

  const [activeTab, setActiveTab] = useState<'keda' | 'terraform'>('keda');

  const hasKeda = !!result?.kedaYaml;
  const hasTerraform = !!result?.terraformHcl;
  const hasBoth = hasKeda && hasTerraform;
  const activeCode = activeTab === 'keda' ? result?.kedaYaml : result?.terraformHcl;

  useEffect(() => {
    if (!resourceId) return;
    setLoading(true);
    setError(null);
    setNoRecommendation(false);
    recommendationsApi
      .generateCode(resourceId)
      .then((res) => {
        if (!res) {
          setResult(null);
          setNoRecommendation(true);
          return;
        }
        setResult(res);
        setActiveTab(res.kedaYaml ? 'keda' : 'terraform');
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to generate code'))
      .finally(() => setLoading(false));
  }, [resourceId]);

  return (
    <div className="page">
      <Link to={`/resources/${resourceId}`} className="back-link">&larr; Back to Resource</Link>
      <h1>Generate Scaling Code</h1>
      <p className="subtitle">Auto-generate scaling configuration for this resource</p>

      {loading && <div className="loading">Generating code...</div>}

      {error && <div className="error">{error}</div>}

      {noRecommendation && !loading && !error && (
        <div className="empty-state">
          <p>No active recommendation for this resource.</p>
          <p>
            Scaling code is generated from an active recommendation, which requires off-peak
            utilization below target and peak utilization above target over the analyzed period.
          </p>
          <Link to={`/resources/${resourceId}`} className="btn btn-primary">
            Back to Resource
          </Link>
        </div>
      )}

      {result && (
        <div className="code-generation-result">
          <div className="rec-summary card">
            <h3>{result.recommendation.resourceName}</h3>
            <p>Type: {result.recommendation.recommendationType.replace(/_/g, ' ')}</p>
            <p>Est. savings: ${result.recommendation.estimatedMonthlySavingsUsd.toFixed(0)}/mo</p>
          </div>

          {hasBoth && (
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
          )}

          {activeCode && (
            <div className="code-block">
              <pre>
                <code>{activeCode}</code>
              </pre>
              <button
                className="btn btn-copy"
                onClick={() => navigator.clipboard.writeText(activeCode)}
              >
                Copy to Clipboard
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

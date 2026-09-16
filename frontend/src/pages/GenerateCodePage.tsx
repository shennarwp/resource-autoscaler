import { useParams, Link } from 'react-router-dom';
import { useState, useEffect, useCallback } from 'react';
import type { KeyboardEvent as ReactKeyboardEvent } from 'react';
import { recommendationsApi } from '../services/api';
import type { RecommendationResponse } from '../types/api';

/** Requests and displays generated KEDA or Terraform scaling configuration. */
export default function GenerateCodePage() {
  const { resourceId } = useParams<{ resourceId: string }>();
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [noRecommendation, setNoRecommendation] = useState(false);

  const [activeTab, setActiveTab] = useState<'keda' | 'terraform'>('keda');
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);

  const hasKeda = !!result?.kedaYaml;
  const hasTerraform = !!result?.terraformHcl;
  const hasBoth = hasKeda && hasTerraform;
  const activeCode = activeTab === 'keda' ? result?.kedaYaml : result?.terraformHcl;

  useEffect(() => {
    if (!resourceId) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    setNoRecommendation(false);
    recommendationsApi
      .generateCode(resourceId, undefined, undefined, 0, controller.signal)
      .then((res) => {
        if (!res) {
          setResult(null);
          setNoRecommendation(true);
          return;
        }
        setResult(res);
        setActiveTab(res.kedaYaml ? 'keda' : 'terraform');
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Failed to generate code');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [resourceId]);

  const copyCode = useCallback(async () => {
    if (!activeCode) return;
    try {
      await navigator.clipboard.writeText(activeCode);
      setCopied(true);
      setCopyError(false);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopyError(true);
      setTimeout(() => setCopyError(false), 2000);
    }
  }, [activeCode]);

  const handleTabKeyDown = useCallback(
    (e: ReactKeyboardEvent, nextTab: 'keda' | 'terraform') => {
      if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
        e.preventDefault();
        setActiveTab(nextTab);
      }
    },
    [],
  );

  return (
    <div className="page">
      <Link to={`/resources/${resourceId}`} className="back-link">&larr; Back to Resource</Link>
      <h1>Generate Scaling Code</h1>
      <p className="subtitle">Auto-generate scaling configuration for this resource</p>

      {loading && <div className="loading" role="status">Generating code...</div>}

      {error && <div className="error" role="alert">{error}</div>}

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
            <div className="code-tabs" role="tablist" aria-label="Generated code format">
              <button
                role="tab"
                id="tab-keda"
                aria-selected={activeTab === 'keda'}
                aria-controls="panel-keda"
                tabIndex={activeTab === 'keda' ? 0 : -1}
                className={`tab ${activeTab === 'keda' ? 'active' : ''}`}
                onClick={() => setActiveTab('keda')}
                onKeyDown={(e) => handleTabKeyDown(e, 'terraform')}
              >
                KEDA ScaledObject (AKS)
              </button>
              <button
                role="tab"
                id="tab-terraform"
                aria-selected={activeTab === 'terraform'}
                aria-controls="panel-terraform"
                tabIndex={activeTab === 'terraform' ? 0 : -1}
                className={`tab ${activeTab === 'terraform' ? 'active' : ''}`}
                onClick={() => setActiveTab('terraform')}
                onKeyDown={(e) => handleTabKeyDown(e, 'keda')}
              >
                Terraform (Azure VM/App Service)
              </button>
            </div>
          )}

          {activeCode && (
            <div
              className="code-block"
              role="tabpanel"
              id={activeTab === 'keda' ? 'panel-keda' : 'panel-terraform'}
              aria-labelledby={activeTab === 'keda' ? 'tab-keda' : 'tab-terraform'}
            >
              <pre>
                <code>{activeCode}</code>
              </pre>
              <div aria-live="polite" className="visually-hidden">
                {copied && 'Code copied to clipboard.'}
                {copyError && 'Failed to copy code.'}
              </div>
              <button
                type="button"
                className={`btn btn-copy ${copied ? 'btn-copy-success' : ''}`}
                onClick={copyCode}
                aria-label={copied ? 'Code copied' : 'Copy code to clipboard'}
              >
                {copied ? 'Copied!' : 'Copy to Clipboard'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

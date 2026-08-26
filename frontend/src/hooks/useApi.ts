import { useState, useEffect } from 'react';
import { metricsApi, costApi, recommendationsApi } from '../services/api';
import type { MetricsResponse, CostAnalysis, ScalingRecommendation } from '../types/api';

export function useMetrics(resourceId: string | null, days = 30) {
  const [metrics, setMetrics] = useState<MetricsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!resourceId) return;
    setLoading(true);
    metricsApi
      .getResourceMetrics(resourceId, days)
      .then(setMetrics)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [resourceId, days]);

  return { metrics, loading: loading && !metrics, error };
}

export function useCostAnalysis() {
  const [analysis, setAnalysis] = useState<CostAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    costApi
      .getCostAnalysis()
      .then(setAnalysis)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return { analysis, loading: loading && !analysis, error };
}

export function useRecommendations(resourceId: string | null, days = 30) {
  const [recommendations, setRecommendations] = useState<ScalingRecommendation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!resourceId) return;
    setLoading(true);
    recommendationsApi
      .getRecommendations(resourceId, days)
      .then(setRecommendations)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [resourceId, days]);

  return { recommendations, loading: loading && recommendations.length === 0, error };
}

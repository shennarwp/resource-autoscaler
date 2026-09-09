import { useState, useEffect } from 'react';
import { metricsApi, costApi, recommendationsApi } from '../services/api';
import type { MetricsResponse, CostAnalysis, ScalingRecommendation, PeakHoursConfig } from '../types/api';

/**
 * Fetches metrics when the resource or time window changes.
 * Existing data is retained while a new window is loading.
 */
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

  return { metrics, loading: loading && !metrics, refreshing: loading && !!metrics, error };
}

/** Fetches a resource's peak schedule while ignoring stale unmount responses. */
export function usePeakHoursConfig(resourceId: string | null): PeakHoursConfig | null {
  const [config, setConfig] = useState<PeakHoursConfig | null>(null);

  useEffect(() => {
    if (!resourceId) return;
    let cancelled = false;
    metricsApi
      .getPeakHoursConfig(resourceId)
      .then((config) => {
        if (!cancelled) setConfig(config);
      })
      .catch((err) => console.warn('Failed to load peak hours config:', err.message));
    return () => {
      cancelled = true;
    };
  }, [resourceId]);

  return config;
}

/** Loads the dashboard-wide cost analysis once when the hook mounts. */
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

  return { analysis, loading: loading && !analysis, refreshing: loading && !!analysis, error };
}

/** Fetches recommendations and distinguishes initial loading from refreshes. */
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

  return { recommendations, loading: loading && recommendations.length === 0, refreshing: loading && recommendations.length > 0, error };
}

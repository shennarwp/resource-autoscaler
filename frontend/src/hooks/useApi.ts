/* oxlint-disable react/set-state-in-effect */
import { useState, useEffect, useCallback } from 'react';
import { metricsApi, costApi, recommendationsApi } from '../services/api';
import type { MetricsResponse, CostAnalysis, ScalingRecommendation, PeakHoursConfig } from '../types/api';

/**
 * Fetches metrics when the resource or time window changes.
 * Existing data is retained while a new window is loading, and stale
 * in-flight requests are aborted when the inputs change or the hook unmounts.
 */
export function useMetrics(resourceId: string | null, days = 30) {
  const [metrics, setMetrics] = useState<MetricsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!resourceId) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    metricsApi
      .getResourceMetrics(resourceId, days, controller.signal)
      .then(setMetrics)
      .catch((err) => {
        if (!controller.signal.aborted) setError(err.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [resourceId, days]);

  return { metrics, loading: loading && !metrics, refreshing: loading && !!metrics, error };
}

/** Fetches a resource's peak schedule while ignoring stale unmount responses. */
export function usePeakHoursConfig(resourceId: string | null): PeakHoursConfig | null {
  const [config, setConfig] = useState<PeakHoursConfig | null>(null);

  useEffect(() => {
    if (!resourceId) return;
    const controller = new AbortController();
    metricsApi
      .getPeakHoursConfig(resourceId, controller.signal)
      .then((next) => {
        if (!controller.signal.aborted) setConfig(next);
      })
      .catch((err) => {
        if (!controller.signal.aborted) console.warn('Failed to load peak hours config:', err.message);
      });
    return () => controller.abort();
  }, [resourceId]);

  return config;
}

/** Loads the dashboard-wide cost analysis, with a {@code reload} replanner. */
export function useCostAnalysis() {
  const [analysis, setAnalysis] = useState<CostAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    costApi
      .getCostAnalysis(controller.signal)
      .then(setAnalysis)
      .catch((err) => {
        if (!controller.signal.aborted) setError(err.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);

  return { analysis, loading: loading && !analysis, refreshing: loading && !!analysis, error, reload };
}

/** Fetches recommendations and distinguishes initial loading from refreshes. */
export function useRecommendations(resourceId: string | null, days = 30) {
  const [recommendations, setRecommendations] = useState<ScalingRecommendation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!resourceId) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    recommendationsApi
      .getRecommendations(resourceId, days, controller.signal)
      .then(setRecommendations)
      .catch((err) => {
        if (!controller.signal.aborted) setError(err.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [resourceId, days]);

  return { recommendations, loading: loading && recommendations.length === 0, refreshing: loading && recommendations.length > 0, error };
}

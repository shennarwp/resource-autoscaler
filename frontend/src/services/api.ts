import axios from 'axios';
import type { MetricsResponse, CostAnalysis, ScalingRecommendation, RecommendationResponse, PeakHoursConfig } from '../types/api';

/** Backend base URL; defaults to a relative path that rides the nginx proxy. Set VITE_API_BASE_URL for local dev without nginx. */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

/** Typed HTTP client for resource metrics endpoints. */
export const metricsApi = {
  /** Lists resource identifiers exposed by the active backend profile. */
  getMonitoredResources: async (): Promise<string[]> => {
    const { data } = await api.get<string[]>('/metrics');
    return data;
  },

  /** Loads samples and aggregates for a resource over the requested number of days. */
  getResourceMetrics: async (resourceId: string, days = 30): Promise<MetricsResponse> => {
    const { data } = await api.get<MetricsResponse>(`/metrics/${resourceId}`, {
      params: { days },
    });
    return data;
  },

  /** Loads the UTC peak-hours schedule and thresholds for a resource. */
  getPeakHoursConfig: async (resourceId: string): Promise<PeakHoursConfig> => {
    const { data } = await api.get<PeakHoursConfig>(`/metrics/${resourceId}/peak-config`);
    return data;
  },
};

/** Typed HTTP client for recommendation and generated-code endpoints. */
export const recommendationsApi = {
  /** Retrieves recommendations for the selected analysis window. */
  getRecommendations: async (resourceId: string, days = 30): Promise<ScalingRecommendation[]> => {
    const { data } = await api.get<ScalingRecommendation[]>(`/recommendations/${resourceId}`, {
      params: { days },
    });
    return data;
  },

  /** Requests code for a resource, returning no body when no recommendation applies. */
  generateCode: async (
    resourceId: string,
    peakStart?: string,
    peakEnd?: string,
    currentMonthlyCostUsd = 0
  ): Promise<RecommendationResponse> => {
    const body: Record<string, unknown> = { resourceId, currentMonthlyCostUsd };
    if (peakStart !== undefined) body.peakStart = peakStart;
    if (peakEnd !== undefined) body.peakEnd = peakEnd;
    const { data } = await api.post<RecommendationResponse>('/recommendations/generate', body);
    return data;
  },
};

/** Typed HTTP client for aggregate cost analysis. */
export const costApi = {
  /** Loads current, optimized, and projected costs for all monitored resources. */
  getCostAnalysis: async (): Promise<CostAnalysis> => {
    const { data } = await api.get<CostAnalysis>('/costs/analysis');
    return data;
  },
};

/** Shared Axios client for callers that need direct access to the API. */
export default api;

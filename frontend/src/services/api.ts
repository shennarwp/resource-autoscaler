import axios from 'axios';
import type { MetricsResponse, CostAnalysis, ScalingRecommendation, RecommendationResponse } from '../types/api';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const metricsApi = {
  getMonitoredResources: async (): Promise<string[]> => {
    const { data } = await api.get<string[]>('/metrics');
    return data;
  },

  getResourceMetrics: async (resourceId: string, days = 30): Promise<MetricsResponse> => {
    const { data } = await api.get<MetricsResponse>(`/metrics/${resourceId}`, {
      params: { days },
    });
    return data;
  },
};

export const recommendationsApi = {
  getRecommendations: async (
    resourceId: string,
    days = 30,
    currentMonthlyCost = 560.0
  ): Promise<ScalingRecommendation[]> => {
    const { data } = await api.get<ScalingRecommendation[]>(`/recommendations/${resourceId}`, {
      params: { days, currentMonthlyCost },
    });
    return data;
  },

  generateCode: async (
    resourceId: string,
    peakStart = '07:00',
    peakEnd = '18:00',
    currentMonthlyCost = 560.0
  ): Promise<RecommendationResponse> => {
    const { data } = await api.post<RecommendationResponse>('/recommendations/generate', {
      resourceId,
      peakStart,
      peakEnd,
      currentMonthlyCostUsd: currentMonthlyCost,
    });
    return data;
  },
};

export const costApi = {
  getCostAnalysis: async (): Promise<CostAnalysis> => {
    const { data } = await api.get<CostAnalysis>('/costs/analysis');
    return data;
  },
};

export default api;

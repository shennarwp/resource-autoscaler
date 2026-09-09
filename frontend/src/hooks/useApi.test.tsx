import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { costApi, metricsApi, recommendationsApi } from '../services/api';
import { useCostAnalysis, useMetrics, usePeakHoursConfig, useRecommendations } from './useApi';
import type { MetricsResponse, PeakHoursConfig, ScalingRecommendation } from '../types/api';

vi.mock('../services/api', () => ({
  metricsApi: {
    getResourceMetrics: vi.fn(),
    getPeakHoursConfig: vi.fn(),
  },
  costApi: { getCostAnalysis: vi.fn() },
  recommendationsApi: { getRecommendations: vi.fn() },
}));

const metrics: MetricsResponse = {
  resourceId: 'vm-1',
  resourceType: 'AZURE_VM',
  resourceName: 'VM 1',
  dataPoints: [],
  stats: {
    avgCpuUtilization: 0,
    maxCpuUtilization: 0,
    avgMemoryUtilization: 0,
    peakHourUtilization: 0,
    offPeakHourUtilization: 0,
  },
};
const recommendation = {
  resourceId: 'vm-1',
  recommendationType: 'RIGHTSIZING',
} as ScalingRecommendation;
const peakConfigFixture = {
  peakStart: '07:00',
  peakEnd: '18:00',
  peakDaysOfWeek: [1, 2, 3, 4, 5],
  peakTargetUtilization: 65,
  offPeakTargetUtilization: 10,
  scalingCooldownMinutes: 5,
} as PeakHoursConfig;

describe('API hooks', () => {
  it('loads metrics and distinguishes initial loading from refresh loading', async () => {
    let resolveRequest: (value: typeof metrics) => void = () => {};
    vi.mocked(metricsApi.getResourceMetrics).mockImplementation(
      () => new Promise((resolve) => { resolveRequest = resolve; }),
    );

    const { result, rerender } = renderHook(({ days }) => useMetrics('vm-1', days), {
      initialProps: { days: 1 },
    });
    expect(result.current.loading).toBe(true);
    expect(result.current.refreshing).toBe(false);

    resolveRequest(metrics);
    await waitFor(() => expect(result.current.metrics).toEqual(metrics));

    let resolveRefresh: (value: typeof metrics) => void = () => {};
    vi.mocked(metricsApi.getResourceMetrics).mockImplementation(
      () => new Promise((resolve) => { resolveRefresh = resolve; }),
    );
    rerender({ days: 7 });
    await waitFor(() => expect(result.current.refreshing).toBe(true));
    expect(result.current.loading).toBe(false);
    resolveRefresh(metrics);
  });

  it('reports metric and cost errors', async () => {
    vi.mocked(metricsApi.getResourceMetrics).mockRejectedValueOnce(new Error('metrics unavailable'));
    vi.mocked(costApi.getCostAnalysis).mockRejectedValueOnce(new Error('cost unavailable'));

    const metric = renderHook(() => useMetrics('vm-1'));
    const cost = renderHook(() => useCostAnalysis());

    await waitFor(() => expect(metric.result.current.error).toBe('metrics unavailable'));
    await waitFor(() => expect(cost.result.current.error).toBe('cost unavailable'));
  });

  it('loads recommendations and peak configuration without requesting when no resource is selected', async () => {
    vi.mocked(recommendationsApi.getRecommendations).mockResolvedValueOnce([recommendation]);
    vi.mocked(metricsApi.getPeakHoursConfig).mockResolvedValueOnce(peakConfigFixture);

    const recommendations = renderHook(() => useRecommendations('vm-1'));
    const peakConfig = renderHook(() => usePeakHoursConfig('vm-1'));
    await waitFor(() => expect(recommendations.result.current.recommendations).toEqual([recommendation]));
    await waitFor(() => expect(peakConfig.result.current).toEqual(peakConfigFixture));

    renderHook(() => useRecommendations(null));
    renderHook(() => useMetrics(null));
    expect(recommendationsApi.getRecommendations).toHaveBeenCalledTimes(1);
  });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({ get, post })),
  },
}));

import api, { costApi, metricsApi, recommendationsApi } from './api';

describe('API clients', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it('uses the typed metrics endpoints and query parameters', async () => {
    get
      .mockResolvedValueOnce({ data: ['vm-1'] })
      .mockResolvedValueOnce({ data: { resourceId: 'vm-1' } })
      .mockResolvedValueOnce({ data: { peakStart: '07:00' } });

    await expect(metricsApi.getMonitoredResources()).resolves.toEqual(['vm-1']);
    await expect(metricsApi.getResourceMetrics('vm-1', 7)).resolves.toEqual({ resourceId: 'vm-1' });
    await expect(metricsApi.getPeakHoursConfig('vm-1')).resolves.toEqual({ peakStart: '07:00' });

    expect(get).toHaveBeenNthCalledWith(1, '/metrics');
    expect(get).toHaveBeenNthCalledWith(2, '/metrics/vm-1', { params: { days: 7 } });
    expect(get).toHaveBeenNthCalledWith(3, '/metrics/vm-1/peak-config');
  });

  it('applies recommendation defaults and posts generated-code requests', async () => {
    get.mockResolvedValueOnce({ data: [] });
    post.mockResolvedValueOnce({ data: { recommendation: {} } });

    await recommendationsApi.getRecommendations('vm-1');
    await recommendationsApi.generateCode('vm-1');

    expect(get).toHaveBeenCalledWith('/recommendations/vm-1', { params: { days: 30 } });
    expect(post).toHaveBeenCalledWith('/recommendations/generate', {
      resourceId: 'vm-1',
      peakStart: '07:00',
      peakEnd: '18:00',
      currentMonthlyCostUsd: 0,
    });
  });

  it('returns cost analysis from the aggregate endpoint and exposes the shared client', async () => {
    get.mockResolvedValueOnce({ data: { resources: [], summary: {} } });

    await expect(costApi.getCostAnalysis()).resolves.toEqual({ resources: [], summary: {} });
    expect(api).toBeDefined();
    expect(get).toHaveBeenCalledWith('/costs/analysis');
  });
});

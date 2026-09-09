import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import ResourceDetailPage from './ResourceDetailPage';
import { useMetrics, usePeakHoursConfig, useRecommendations } from '../hooks/useApi';

vi.mock('../hooks/useApi', () => ({
  useMetrics: vi.fn(),
  usePeakHoursConfig: vi.fn(),
  useRecommendations: vi.fn(),
}));
vi.mock('recharts', () => {
  const Chart = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  const Reference = ({ label }: { label?: string }) => <span>{label}</span>;
  return {
    LineChart: Chart, Line: Chart, XAxis: Chart, YAxis: Chart, CartesianGrid: Chart,
    Tooltip: Chart, Legend: Chart, ResponsiveContainer: Chart, AreaChart: Chart,
    Area: Chart, ReferenceLine: Reference,
  };
});

const renderPage = () => render(
  <MemoryRouter initialEntries={['/resources/function-data-processor']}>
    <Routes><Route path="/resources/:resourceId" element={<ResourceDetailPage />} /></Routes>
  </MemoryRouter>,
);

const metrics = {
  resourceId: 'function-data-processor',
  resourceName: 'Data Processor',
  resourceType: 'AZURE_FUNCTION',
  dataPoints: [
    { timestamp: '2026-01-01T01:00:00Z', cpuUtilization: 20.126, memoryUtilization: 30.5, activeRequestCount: 2 },
    { timestamp: '2026-01-01T00:00:00Z', cpuUtilization: 10, memoryUtilization: 20, activeRequestCount: 1 },
  ],
  stats: { avgCpuUtilization: 15, maxCpuUtilization: 20, peakHourUtilization: 25, offPeakHourUtilization: 8, avgMemoryUtilization: 25 },
};

describe('ResourceDetailPage', () => {
  it('renders loading and not-found states', () => {
    vi.mocked(useMetrics).mockReturnValue({ metrics: null, loading: true, refreshing: false, error: null } as ReturnType<typeof useMetrics>);
    vi.mocked(useRecommendations).mockReturnValue({ recommendations: [], loading: false, refreshing: false, error: null } as ReturnType<typeof useRecommendations>);
    vi.mocked(usePeakHoursConfig).mockReturnValue(null);
    renderPage();
    expect(screen.getByText('Loading metrics...')).toBeInTheDocument();

    vi.mocked(useMetrics).mockReturnValue({ metrics: null, loading: false, refreshing: false, error: 'failed' } as ReturnType<typeof useMetrics>);
    renderPage();
    expect(screen.getByText('Resource not found')).toBeInTheDocument();
  });

  it('renders metrics, fallback targets, recommendations, and changes time range', async () => {
    vi.mocked(useMetrics).mockReturnValue({ metrics, loading: false, refreshing: false, error: null } as ReturnType<typeof useMetrics>);
    vi.mocked(useRecommendations).mockReturnValue({
      recommendations: [{
        recommendationType: 'RIGHTSIZING',
        estimatedMonthlySavingsUsd: 20,
        estimatedSavingsPercentage: 10,
        currentConfiguration: 'current',
        recommendedConfiguration: 'recommended',
        rationale: 'Scale during quiet periods',
        confidenceScore: 0.9,
        peakSchedule: '07:00-18:00 UTC',
      }],
      loading: false,
      refreshing: false,
      error: null,
    } as ReturnType<typeof useRecommendations>);
    vi.mocked(usePeakHoursConfig).mockReturnValue(null);
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole('heading', { name: 'Data Processor' })).toBeInTheDocument();
    expect(screen.getByText('10.0', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('RIGHTSIZING')).toBeInTheDocument();
    expect(screen.getByText('Confidence: 90%')).toBeInTheDocument();
    expect(screen.getByText('Peak Target 55%')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Generate Scaling Code/ })).toHaveAttribute('href', '/resources/function-data-processor/generate');

    await user.click(screen.getByRole('button', { name: '1w' }));
    await waitFor(() => expect(useMetrics).toHaveBeenLastCalledWith('function-data-processor', 7));
  });
});

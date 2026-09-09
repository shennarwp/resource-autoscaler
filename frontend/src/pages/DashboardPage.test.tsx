import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DashboardPage from './DashboardPage';
import { metricsApi } from '../services/api';
import { useCostAnalysis } from '../hooks/useApi';

vi.mock('../services/api', () => ({
  metricsApi: { getMonitoredResources: vi.fn() },
}));
vi.mock('../hooks/useApi', () => ({ useCostAnalysis: vi.fn() }));

const renderPage = () => render(<MemoryRouter><DashboardPage /></MemoryRouter>);

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.mocked(metricsApi.getMonitoredResources).mockResolvedValue(['vm-1']);
    vi.mocked(useCostAnalysis).mockReturnValue({
      analysis: {
        summary: {
          totalCurrentCostUsd: 100,
          totalOptimizedCostUsd: 75,
          totalPotentialSavingsUsd: 25,
          overallSavingsPercentage: 25,
          estimatedAnnualSavingsUsd: '300',
        },
      },
      loading: false,
      refreshing: false,
      error: null,
    } as ReturnType<typeof useCostAnalysis>);
  });

  it('renders summary cards and links each monitored resource', async () => {
    renderPage();
    expect(screen.getByText('$100')).toBeInTheDocument();
    expect(screen.getByText('$75')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('link')).toHaveAttribute('href', '/resources/vm-1'));
  });

  it('shows the empty state when no resources are returned after loading', async () => {
    vi.mocked(metricsApi.getMonitoredResources).mockResolvedValueOnce([]);
    vi.mocked(useCostAnalysis).mockReturnValue({
      analysis: null,
      loading: false,
      refreshing: false,
      error: null,
    } as ReturnType<typeof useCostAnalysis>);

    renderPage();
    await waitFor(() => expect(screen.getByText(/No resources being monitored/)).toBeInTheDocument());
  });
});

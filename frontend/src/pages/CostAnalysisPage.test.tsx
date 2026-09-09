import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import CostAnalysisPage from './CostAnalysisPage';
import { useCostAnalysis } from '../hooks/useApi';

vi.mock('../hooks/useApi', () => ({ useCostAnalysis: vi.fn() }));
vi.mock('recharts', () => {
  const Chart = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  return {
    BarChart: Chart, Bar: Chart, XAxis: Chart, YAxis: Chart, CartesianGrid: Chart,
    Tooltip: Chart, ResponsiveContainer: Chart,
  };
});

const renderPage = () => render(<MemoryRouter><CostAnalysisPage /></MemoryRouter>);

describe('CostAnalysisPage', () => {
  it('renders loading, error, and empty states', () => {
    vi.mocked(useCostAnalysis).mockReturnValue({ analysis: null, loading: true, refreshing: false, error: null } as ReturnType<typeof useCostAnalysis>);
    renderPage();
    expect(screen.getByText('Analyzing costs...')).toBeInTheDocument();

    vi.mocked(useCostAnalysis).mockReturnValue({ analysis: null, loading: false, refreshing: false, error: 'Failed' } as ReturnType<typeof useCostAnalysis>);
    renderPage();
    expect(screen.getByText('Failed')).toBeInTheDocument();

    vi.mocked(useCostAnalysis).mockReturnValue({
      analysis: { resources: [] } as unknown as NonNullable<ReturnType<typeof useCostAnalysis>['analysis']>,
      loading: false,
      refreshing: false,
      error: null,
    });
    renderPage();
    expect(screen.getByText('No resources to analyze.')).toBeInTheDocument();
  });

  it('renders resource costs, savings, and details links', () => {
    vi.mocked(useCostAnalysis).mockReturnValue({
      analysis: {
        resources: [{
          resourceId: 'vm-1',
          resourceName: 'Backend VM',
          resourceType: 'AZURE_VM',
          currentMonthlyCostUsd: 100,
          optimizedMonthlyCostUsd: 80,
          potentialSavingsUsd: 20,
          savingsPercentage: 20,
          currentPeakCpuPercent: 70,
          currentOffPeakCpuPercent: 10,
        }],
      },
      loading: false,
      refreshing: false,
      error: null,
    } as ReturnType<typeof useCostAnalysis>);
    renderPage();
    expect(screen.getByText('Backend VM')).toBeInTheDocument();
    expect(screen.getByText('$20.00 (20.0%)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Details' })).toHaveAttribute('href', '/resources/vm-1');
  });
});

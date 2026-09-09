import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('./pages/DashboardPage', () => ({ default: () => <h1>Dashboard route</h1> }));
vi.mock('./pages/CostAnalysisPage', () => ({ default: () => <h1>Cost route</h1> }));
vi.mock('./pages/ResourceDetailPage', () => ({ default: () => <h1>Resource route</h1> }));
vi.mock('./pages/GenerateCodePage', () => ({ default: () => <h1>Generate route</h1> }));

describe('App routing shell', () => {
  it('renders shared navigation and navigates to cost analysis', async () => {
    render(<App />);
    expect(screen.getByRole('link', { name: 'Resource Autoscaler' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Cost Analysis' })).toHaveAttribute('href', '/costs');
  });
});

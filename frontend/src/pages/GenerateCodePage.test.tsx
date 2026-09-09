import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import GenerateCodePage from './GenerateCodePage';
import { recommendationsApi } from '../services/api';

vi.mock('../services/api', () => ({
  recommendationsApi: { generateCode: vi.fn() },
}));

const renderPage = () => render(
  <MemoryRouter initialEntries={['/resources/vm-1/generate']}>
    <Routes><Route path="/resources/:resourceId/generate" element={<GenerateCodePage />} /></Routes>
  </MemoryRouter>,
);

describe('GenerateCodePage', () => {
  it('renders no-recommendation and error states', async () => {
    vi.mocked(recommendationsApi.generateCode).mockResolvedValueOnce(null as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('No active recommendation for this resource.')).toBeInTheDocument());

    vi.mocked(recommendationsApi.generateCode).mockRejectedValueOnce(new Error('generation failed'));
    renderPage();
    await waitFor(() => expect(screen.getByText('generation failed')).toBeInTheDocument());
  });

  it('switches generated-code tabs and copies the active code', async () => {
    vi.mocked(recommendationsApi.generateCode).mockResolvedValueOnce({
      recommendation: {
        resourceName: 'Backend VM',
        recommendationType: 'SCHEDULE_BASED_SCALING',
        estimatedMonthlySavingsUsd: 25,
      },
      kedaYaml: 'keda-code',
      terraformHcl: 'terraform-code',
    } as never);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByText('keda-code')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /Terraform/ }));
    expect(screen.getByText('terraform-code')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copy to Clipboard' })).toBeInTheDocument();
  });
});

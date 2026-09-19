import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ResourceDetailPage = lazy(() => import('./pages/ResourceDetailPage'));
const GenerateCodePage = lazy(() => import('./pages/GenerateCodePage'));
const CostAnalysisPage = lazy(() => import('./pages/CostAnalysisPage'));
import ErrorBoundary from './components/ErrorBoundary';

/** Defines the client-side routes and shared navigation shell. */
function App() {
  return (
    <BrowserRouter>
      <div className="app">
        <a className="skip-link" href="#main-content">Skip to main content</a>
        <nav className="navbar" aria-label="Main">
          <div className="nav-brand">
            <Link to="/">Resource Autoscaler</Link>
          </div>
          <div className="nav-links">
            <Link to="/">Dashboard</Link>
            <Link to="/costs">Cost Analysis</Link>
          </div>
        </nav>
        <main id="main-content" className="main-content">
          <ErrorBoundary>
            <Suspense fallback={<div className="loading" role="status">Loading page...</div>}><Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/resources/:resourceId" element={<ResourceDetailPage />} />
              <Route path="/resources/:resourceId/generate" element={<GenerateCodePage />} />
              <Route path="/costs" element={<CostAnalysisPage />} />
            </Routes></Suspense>
          </ErrorBoundary>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;

import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import DashboardPage from './pages/DashboardPage';
import ResourceDetailPage from './pages/ResourceDetailPage';
import GenerateCodePage from './pages/GenerateCodePage';
import CostAnalysisPage from './pages/CostAnalysisPage';
import ErrorBoundary from './components/ErrorBoundary';

/** Defines the client-side routes and shared navigation shell. */
function App() {
  return (
    <BrowserRouter>
      <div className="app">
        <nav className="navbar">
          <div className="nav-brand">
            <Link to="/">Resource Autoscaler</Link>
          </div>
          <div className="nav-links">
            <Link to="/">Dashboard</Link>
            <Link to="/costs">Cost Analysis</Link>
          </div>
        </nav>
        <main className="main-content">
          <ErrorBoundary>
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/resources/:resourceId" element={<ResourceDetailPage />} />
              <Route path="/resources/:resourceId/generate" element={<GenerateCodePage />} />
              <Route path="/costs" element={<CostAnalysisPage />} />
            </Routes>
          </ErrorBoundary>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;

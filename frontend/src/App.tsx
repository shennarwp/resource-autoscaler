import { BrowserRouter, Routes, Route } from 'react-router-dom';
import DashboardPage from './pages/DashboardPage';
import ResourceDetailPage from './pages/ResourceDetailPage';
import GenerateCodePage from './pages/GenerateCodePage';
import CostAnalysisPage from './pages/CostAnalysisPage';

function App() {
  return (
    <BrowserRouter>
      <div className="app">
        <nav className="navbar">
          <div className="nav-brand">
            <a href="/">Resource Autoscaler</a>
          </div>
          <div className="nav-links">
            <a href="/">Dashboard</a>
            <a href="/costs">Cost Analysis</a>
          </div>
        </nav>
        <main className="main-content">
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/resources/:resourceId" element={<ResourceDetailPage />} />
            <Route path="/resources/:resourceId/generate" element={<GenerateCodePage />} />
            <Route path="/costs" element={<CostAnalysisPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;

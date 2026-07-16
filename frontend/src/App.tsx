import { NavLink, Route, Routes } from "react-router-dom";
import AddObservationPage from "./pages/AddObservationPage";
import DashboardPage from "./pages/DashboardPage";
import NewReportPage from "./pages/NewReportPage";
import ReportDetailPage from "./pages/ReportDetailPage";
import ReportsListPage from "./pages/ReportsListPage";
import TrendPage from "./pages/TrendPage";

function App() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">Medical Report Analytics</p>
          <h1>Lab Trends</h1>
        </div>
        <nav className="nav-links" aria-label="Primary navigation">
          <NavLink to="/">Dashboard</NavLink>
          <NavLink to="/reports">Reports</NavLink>
          <NavLink to="/reports/new">New Report</NavLink>
          <NavLink to="/observations/new">Add Observation</NavLink>
          <NavLink to="/tests/00000000-0000-4000-8000-000000000101/trend">
            Hemoglobin Trend
          </NavLink>
        </nav>
      </header>

      <main className="page-content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/reports" element={<ReportsListPage />} />
          <Route path="/reports/new" element={<NewReportPage />} />
          <Route path="/reports/:reportId" element={<ReportDetailPage />} />
          <Route path="/observations/new" element={<AddObservationPage />} />
          <Route path="/tests/:testId/trend" element={<TrendPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;

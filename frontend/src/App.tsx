import { NavLink, Route, Routes } from "react-router-dom";
import AddObservationPage from "./pages/AddObservationPage";
import DashboardPage from "./pages/DashboardPage";
import NewReportPage from "./pages/NewReportPage";
import ReportDetailPage from "./pages/ReportDetailPage";
import ReportsListPage from "./pages/ReportsListPage";
import TrendPage from "./pages/TrendPage";
import soveraHealthIcon from "./assets/brand/soverahealth-icon.png";
import soveraHealthWordmark from "./assets/brand/soverahealth-wordmark.png";

function App() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-lockup">
          <picture>
            <source media="(max-width: 700px)" srcSet={soveraHealthIcon} />
            <img
              className="brand-logo"
              src={soveraHealthWordmark}
              alt="SoveraHealth"
            />
          </picture>
          <div className="brand-divider" aria-hidden="true" />
          <h1 className="module-label">Diagnostic Reports</h1>
        </div>
        <nav className="nav-links" aria-label="Primary navigation">
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/reports" end>
            Reports
          </NavLink>
          <NavLink to="/reports/new">Upload Report</NavLink>
          <NavLink to="/observations/new">Add Observation</NavLink>
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

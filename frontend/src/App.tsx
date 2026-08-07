import { ChangeEvent, useState } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import {
  DEV_USERS,
  DevUserKey,
  getCurrentDevUser,
  setCurrentDevUser
} from "./api/client";
import AddObservationPage from "./pages/AddObservationPage";
import DashboardPage from "./pages/DashboardPage";
import NewReportPage from "./pages/NewReportPage";
import ReportDetailPage from "./pages/ReportDetailPage";
import ReportsListPage from "./pages/ReportsListPage";
import TrendPage from "./pages/TrendPage";
import TrendsPage from "./pages/TrendsPage";
import soveraHealthWordmark from "./assets/brand/soverahealth-wordmark.png";

function App() {
  const [selectedDevUser, setSelectedDevUser] = useState(getCurrentDevUser);

  function handleDevUserChange(event: ChangeEvent<HTMLSelectElement>) {
    setSelectedDevUser(setCurrentDevUser(event.target.value as DevUserKey));
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-lockup">
          <img
            className="brand-logo"
            src={soveraHealthWordmark}
            alt="SoveraHealth"
          />
        </div>
        <div className="header-controls">
          <nav className="nav-links" aria-label="Primary navigation">
            <NavLink to="/" end>
              Dashboard
            </NavLink>
            <NavLink to="/reports" end>
              Reports
            </NavLink>
            <NavLink to="/reports/new">Upload Report</NavLink>
            <NavLink to="/observations/new">Add Observation</NavLink>
            <NavLink to="/trends">Trends</NavLink>
          </nav>
          <div className="dev-user-switcher" aria-label="Development user">
            <span className="dev-user-kicker">DEV</span>
            <select
              aria-label="Select development user"
              value={selectedDevUser.key}
              onChange={handleDevUserChange}
            >
              {DEV_USERS.map((user) => (
                <option key={user.key} value={user.key}>
                  {user.label}
                </option>
              ))}
            </select>
            <span className="dev-user-role">{selectedDevUser.role}</span>
          </div>
        </div>
      </header>

      <main className="page-content" key={selectedDevUser.userId}>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/reports" element={<ReportsListPage />} />
          <Route path="/reports/new" element={<NewReportPage />} />
          <Route path="/reports/:reportId" element={<ReportDetailPage />} />
          <Route path="/observations/new" element={<AddObservationPage />} />
          <Route path="/trends" element={<TrendsPage />} />
          <Route path="/tests/:testId/trend" element={<TrendPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;

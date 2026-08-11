import { ChangeEvent, useState } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import {
  DEV_USERS,
  getCurrentDevUser,
  setCurrentDevUser
} from "./api/client";
import type { DevUser, DevUserKey } from "./api/client";
import AddObservationPage from "./pages/AddObservationPage";
import ActivityPage from "./pages/ActivityPage";
import CareTeamPage from "./pages/CareTeamPage";
import DashboardPage from "./pages/DashboardPage";
import NewReportPage from "./pages/NewReportPage";
import PatientsPage from "./pages/PatientsPage";
import ReportDetailPage from "./pages/ReportDetailPage";
import ReportsListPage from "./pages/ReportsListPage";
import ReviewQueuePage from "./pages/ReviewQueuePage";
import TrendPage from "./pages/TrendPage";
import TrendsPage from "./pages/TrendsPage";
import soveraHealthWordmark from "./assets/brand/soverahealth-wordmark.png";

function UnavailableForRolePage({ devUser }: { devUser: DevUser }) {
  return (
    <section className="page-section">
      <p className="eyebrow">{devUser.role}</p>
      <h2 className="page-title">Action unavailable</h2>
      <p className="page-description">
        This development role can view assigned patient data, but cannot create
        reports or manual observations yet.
      </p>
    </section>
  );
}

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
            {selectedDevUser.role === "CLINICIAN" ? (
              <NavLink to="/patients">Patients</NavLink>
            ) : null}
            <NavLink to="/reports" end>
              Reports
            </NavLink>
            {selectedDevUser.role === "PATIENT" ? (
              <>
                <NavLink to="/reports/new">Upload Report</NavLink>
                <NavLink to="/observations/new">Add Observation</NavLink>
                <NavLink to="/care-team">Care Team</NavLink>
              </>
            ) : null}
            <NavLink to="/trends">Trends</NavLink>
            <NavLink to="/review">Review Queue</NavLink>
            <NavLink to="/activity">Activity</NavLink>
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
          <Route path="/" element={<DashboardPage devUser={selectedDevUser} />} />
          <Route path="/patients" element={<PatientsPage devUser={selectedDevUser} />} />
          <Route
            path="/reports"
            element={<ReportsListPage devUser={selectedDevUser} />}
          />
          <Route
            path="/reports/new"
            element={
              selectedDevUser.role === "PATIENT" ? (
                <NewReportPage />
              ) : (
                <UnavailableForRolePage devUser={selectedDevUser} />
              )
            }
          />
          <Route
            path="/reports/:reportId"
            element={<ReportDetailPage devUser={selectedDevUser} />}
          />
          <Route
            path="/observations/new"
            element={
              selectedDevUser.role === "PATIENT" ? (
                <AddObservationPage />
              ) : (
                <UnavailableForRolePage devUser={selectedDevUser} />
              )
            }
          />
          <Route
            path="/care-team"
            element={<CareTeamPage devUser={selectedDevUser} />}
          />
          <Route path="/trends" element={<TrendsPage devUser={selectedDevUser} />} />
          <Route
            path="/review"
            element={<ReviewQueuePage devUser={selectedDevUser} />}
          />
          <Route
            path="/activity"
            element={<ActivityPage devUser={selectedDevUser} />}
          />
          <Route
            path="/tests/:testId/trend"
            element={<TrendPage devUser={selectedDevUser} />}
          />
        </Routes>
      </main>
    </div>
  );
}

export default App;

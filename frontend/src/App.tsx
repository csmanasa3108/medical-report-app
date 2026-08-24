import { ChangeEvent, useState } from "react";
import { NavLink, Route, Routes, useLocation } from "react-router-dom";
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
import VaultSettingsPage from "./pages/VaultSettingsPage";
import soveraHealthWordmark from "./assets/brand/soverahealth-wordmark.png";
import { getPatientVaultMode } from "./vault/config";
import LocalVaultGate from "./vault/local/LocalVaultGate";

type NavItem = {
  label: string;
  to: string;
  end?: boolean;
};

const patientNavItems: NavItem[] = [
  { label: "Dashboard", to: "/dashboard" },
  { label: "Reports", to: "/reports", end: true },
  { label: "Review Queue", to: "/review" },
  { label: "Trends", to: "/trends" },
  { label: "Activity", to: "/activity" },
  { label: "Care Team", to: "/care-team" }
];

const localPatientNavItems: NavItem[] = [
  ...patientNavItems,
  { label: "Vault Settings", to: "/vault-settings" }
];

const clinicianNavItems: NavItem[] = [
  { label: "Dashboard", to: "/dashboard" },
  { label: "Patients", to: "/patients" },
  { label: "Reports", to: "/reports", end: true },
  { label: "Review Queue", to: "/review" },
  { label: "Trends", to: "/trends" },
  { label: "Activity", to: "/activity" }
];

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

function SidebarNavigation({ devUser }: { devUser: DevUser }) {
  const location = useLocation();
  const isLocalVaultMode = getPatientVaultMode() === "local";
  const navItems =
    devUser.role === "CLINICIAN"
      ? clinicianNavItems
      : isLocalVaultMode
        ? localPatientNavItems
        : patientNavItems;

  return (
    <aside className="sidebar-nav" aria-label="Primary navigation">
      <div className="sidebar-nav-header">
        <span className="sidebar-kicker">
          {devUser.role === "CLINICIAN"
            ? "Clinician workspace"
            : "Patient workspace"}
        </span>
        <span className="sidebar-title">Navigation</span>
      </div>
      <nav className="sidebar-links">
        {navItems.map((item) => {
          const isDashboardRoot =
            item.to === "/dashboard" && location.pathname === "/";

          return (
            <NavLink
              className={({ isActive }) =>
                isActive || isDashboardRoot
                  ? "sidebar-link active"
                  : "sidebar-link"
              }
              end={item.end}
              key={item.to}
              to={item.to}
            >
              {item.label}
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
}

function DemoSafetyBanner() {
  const vaultMode = getPatientVaultMode();
  const isLocalVaultMode = vaultMode === "local";

  return (
    <section className="demo-safety-banner" aria-label="Demo safety notice">
      <div>
        <strong>Demo only - do not enter real medical data.</strong>
        <span>
          {isLocalVaultMode
            ? "Local vault data is encrypted in this browser only. Lost passphrase means no recovery."
            : "API demo mode may store synthetic demo report data on the backend."}
        </span>
      </div>
      <span className="demo-mode-label">
        Mode: {isLocalVaultMode ? "Local encrypted vault prototype" : "API demo"}
      </span>
    </section>
  );
}

function App() {
  const [selectedDevUser, setSelectedDevUser] = useState(getCurrentDevUser);

  function handleDevUserChange(event: ChangeEvent<HTMLSelectElement>) {
    setSelectedDevUser(setCurrentDevUser(event.target.value as DevUserKey));
  }

  return (
    <LocalVaultGate>
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

        <DemoSafetyBanner />

        <div className="app-body">
          <SidebarNavigation devUser={selectedDevUser} />
          <main className="page-content" key={selectedDevUser.userId}>
            <Routes>
              <Route
                path="/"
                element={<DashboardPage devUser={selectedDevUser} />}
              />
              <Route
                path="/dashboard"
                element={<DashboardPage devUser={selectedDevUser} />}
              />
              <Route
                path="/patients"
                element={<PatientsPage devUser={selectedDevUser} />}
              />
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
                path="/upload"
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
              <Route
                path="/trends"
                element={<TrendsPage devUser={selectedDevUser} />}
              />
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
              <Route
                path="/vault-settings"
                element={
                  selectedDevUser.role === "PATIENT" ? (
                    <VaultSettingsPage />
                  ) : (
                    <UnavailableForRolePage devUser={selectedDevUser} />
                  )
                }
              />
            </Routes>
          </main>
        </div>
      </div>
    </LocalVaultGate>
  );
}

export default App;

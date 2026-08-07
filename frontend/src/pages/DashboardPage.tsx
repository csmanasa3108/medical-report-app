import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getReports } from "../api/client";
import type { DevUser } from "../api/client";

type DashboardAction = {
  title: string;
  description: string;
  to: string;
  tone: string;
  meta: string;
};

const patientDashboardActions: DashboardAction[] = [
  {
    title: "Upload Report",
    description: "Add a diagnostic report with report date and lab metadata.",
    to: "/reports/new",
    tone: "primary",
    meta: "New report"
  },
  {
    title: "My Reports",
    description: "Review extracted observations from your uploaded reports.",
    to: "/reports",
    tone: "teal",
    meta: "Report library"
  },
  {
    title: "Add Manual Observation",
    description: "Enter lab observations directly for trend tracking.",
    to: "/observations/new",
    tone: "sand",
    meta: "Manual entry"
  },
  {
    title: "My Trends",
    description: "Open your longitudinal lab trend views.",
    to: "/trends",
    tone: "teal",
    meta: "Trend analytics"
  }
];

const clinicianDashboardActions: DashboardAction[] = [
  {
    title: "Assigned Patients",
    description: "Review patients connected through active clinician access.",
    to: "/patients",
    tone: "primary",
    meta: "Patient panel"
  },
  {
    title: "Patient reports",
    description: "View reports for the selected assigned patient.",
    to: "/reports",
    tone: "teal",
    meta: "Assigned data"
  },
  {
    title: "Patient trends",
    description: "Open trend views for the selected assigned patient.",
    to: "/trends",
    tone: "teal",
    meta: "Trend analytics"
  }
];

type DashboardPageProps = {
  devUser: DevUser;
};

function PatientDashboard({
  reportCountValue,
  reportCountError
}: {
  reportCountValue: string;
  reportCountError: string;
}) {
  return (
    <section className="dashboard-page">
      <div className="dashboard-hero">
        <div>
          <p className="eyebrow">SoveraHealth</p>
          <h2 className="dashboard-title">Your reports</h2>
          <p className="dashboard-subtitle">
            Upload diagnostic reports, review extracted observations, and track
            your trends over time.
          </p>
        </div>
      </div>

      <div className="dashboard-summary-grid" aria-label="Dashboard summary">
        <article className="dashboard-summary-card">
          <span className="dashboard-summary-label">Your reports</span>
          <strong className="dashboard-summary-value">{reportCountValue}</strong>
          <span className="dashboard-summary-detail">
            {reportCountError || "Derived from your reports API"}
          </span>
        </article>
      </div>

      <DashboardActionGrid actions={patientDashboardActions} />
    </section>
  );
}

function ClinicianDashboard({
  reportCountValue,
  reportCountError
}: {
  reportCountValue: string;
  reportCountError: string;
}) {
  return (
    <section className="dashboard-page">
      <div className="dashboard-hero clinician-dashboard-hero">
        <div>
          <p className="eyebrow">Clinician view</p>
          <h2 className="dashboard-title">Assigned Patients</h2>
          <p className="dashboard-subtitle">
            Review assigned patient reports and trends. Upload and observation
            editing actions are hidden for clinician testing.
          </p>
        </div>
      </div>

      <div className="dashboard-summary-grid clinician-summary-grid" aria-label="Clinician dashboard summary">
        <article className="dashboard-summary-card">
          <span className="dashboard-summary-label">Selected patient summary</span>
          <strong className="dashboard-summary-value">{reportCountValue}</strong>
          <span className="dashboard-summary-detail">
            {reportCountError || "Reports available for the current assigned patient"}
          </span>
        </article>
        <article className="dashboard-placeholder-card">
          <span className="dashboard-summary-label">Assigned Patients</span>
          <p>
            Assigned patient list will appear here once the patient assignment
            API is added.
          </p>
        </article>
      </div>

      <DashboardActionGrid actions={clinicianDashboardActions} />
    </section>
  );
}

function DashboardActionGrid({ actions }: { actions: DashboardAction[] }) {
  return (
    <div className="dashboard-action-grid" aria-label="Dashboard actions">
      {actions.map((action) => (
        <Link
          className={`dashboard-action-card dashboard-action-card-${action.tone}`}
          key={action.title}
          to={action.to}
        >
          <span className="dashboard-action-accent" aria-hidden="true" />
          <span className="dashboard-action-meta">{action.meta}</span>
          <span className="dashboard-action-title">{action.title}</span>
          <span className="dashboard-action-description">
            {action.description}
          </span>
          <span className="dashboard-action-cta">Open</span>
        </Link>
      ))}
    </div>
  );
}

function DashboardPage({ devUser }: DashboardPageProps) {
  const [reportCount, setReportCount] = useState<number | null>(null);
  const [isLoadingReportCount, setIsLoadingReportCount] = useState(true);
  const [reportCountError, setReportCountError] = useState("");

  useEffect(() => {
    let isCurrent = true;

    getReports()
      .then((reports) => {
        if (isCurrent) {
          setReportCount(reports.length);
        }
      })
      .catch(() => {
        if (isCurrent) {
          setReportCountError("Report count unavailable");
        }
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoadingReportCount(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, []);

  const reportCountValue = isLoadingReportCount
    ? "Loading"
    : reportCountError
      ? "Unavailable"
      : reportCount?.toLocaleString() ?? "Unavailable";

  if (devUser.role === "CLINICIAN") {
    return (
      <ClinicianDashboard
        reportCountValue={reportCountValue}
        reportCountError={reportCountError}
      />
    );
  }

  return (
    <PatientDashboard
      reportCountValue={reportCountValue}
      reportCountError={reportCountError}
    />
  );
}

export default DashboardPage;

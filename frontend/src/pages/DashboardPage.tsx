import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getReports } from "../api/client";

const dashboardActions = [
  {
    title: "Upload Report",
    description: "Add a diagnostic report with report date and lab metadata.",
    to: "/reports/new",
    tone: "primary",
    meta: "New report"
  },
  {
    title: "View Reports",
    description: "Review uploaded report records and report details.",
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
    title: "View Hemoglobin Trend",
    description: "Open the current trend view for longitudinal comparison.",
    to: "/tests/00000000-0000-4000-8000-000000000101/trend",
    tone: "teal",
    meta: "Trend analytics"
  }
];

function DashboardPage() {
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

  return (
    <section className="dashboard-page">
      <div className="dashboard-hero">
        <div>
          <p className="eyebrow">SoveraHealth</p>
          <h2 className="dashboard-title">Diagnostic Reports</h2>
          <p className="dashboard-subtitle">
            Upload diagnostic reports, maintain manual lab observations, and
            track longitudinal trends over time.
          </p>
        </div>
      </div>

      <div className="dashboard-summary-grid" aria-label="Dashboard summary">
        <article className="dashboard-summary-card">
          <span className="dashboard-summary-label">Total reports uploaded</span>
          <strong className="dashboard-summary-value">{reportCountValue}</strong>
          <span className="dashboard-summary-detail">
            {reportCountError || "Derived from the reports API"}
          </span>
        </article>
      </div>

      <div className="dashboard-action-grid" aria-label="Dashboard actions">
        {dashboardActions.map((action) => (
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
    </section>
  );
}

export default DashboardPage;

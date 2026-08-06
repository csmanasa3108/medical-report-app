import { Link } from "react-router-dom";

const dashboardActions = [
  {
    title: "Upload Report",
    description: "Add a new lab report PDF with report date and lab metadata.",
    to: "/reports/new",
    tone: "primary"
  },
  {
    title: "View Reports",
    description: "Review uploaded report records and observation review status.",
    to: "/reports",
    tone: "teal"
  },
  {
    title: "Add Manual Observation",
    description: "Enter lab observations directly for trend tracking.",
    to: "/observations/new",
    tone: "sand"
  },
  {
    title: "View Hemoglobin Trend",
    description: "Open the current trend view for longitudinal comparison.",
    to: "/tests/00000000-0000-4000-8000-000000000101/trend",
    tone: "teal"
  }
];

function DashboardPage() {
  return (
    <section className="dashboard-page">
      <div className="dashboard-hero">
        <div>
          <p className="eyebrow">SoveraHealth</p>
          <h2 className="dashboard-title">Diagnostic Reports</h2>
          <p className="dashboard-subtitle">
            Upload lab reports, review extracted observations, and track
            diagnostic trends over time.
          </p>
        </div>
      </div>

      <div className="dashboard-action-grid" aria-label="Dashboard actions">
        {dashboardActions.map((action) => (
          <Link
            className={`dashboard-action-card dashboard-action-card-${action.tone}`}
            key={action.title}
            to={action.to}
          >
            <span className="dashboard-action-accent" aria-hidden="true" />
            <span className="dashboard-action-title">{action.title}</span>
            <span className="dashboard-action-description">
              {action.description}
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}

export default DashboardPage;

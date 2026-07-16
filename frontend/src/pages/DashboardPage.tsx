import { Link } from "react-router-dom";

function DashboardPage() {
  return (
    <section className="page-section">
      <h2>Dashboard</h2>
      <p>
        View recent report metadata and lab observation summaries here as the
        manual entry workflow is built out.
      </p>
      <div className="dashboard-actions">
        <Link className="button-link" to="/reports">
          Reports
        </Link>
        <Link className="button-link secondary" to="/reports/new">
          New Report
        </Link>
      </div>
    </section>
  );
}

export default DashboardPage;

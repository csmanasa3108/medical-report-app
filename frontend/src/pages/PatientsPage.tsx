import { Link } from "react-router-dom";
import type { DevUser } from "../api/client";

type PatientsPageProps = {
  devUser: DevUser;
};

function PatientsPage({ devUser }: PatientsPageProps) {
  if (devUser.role !== "CLINICIAN") {
    return (
      <section className="page-section">
        <p className="eyebrow">Patients</p>
        <h2 className="page-title">Patients</h2>
        <p className="page-description">
          Patient assignment tools are available only in the clinician
          development view.
        </p>
      </section>
    );
  }

  return (
    <section className="page-section patients-page">
      <div className="section-header">
        <div>
          <p className="eyebrow">Clinician view</p>
          <h2 className="page-title">Assigned Patients</h2>
          <p className="page-description">
            Select an assigned patient to review reports and trends.
          </p>
        </div>
      </div>

      <div className="patients-layout">
        <article className="dashboard-placeholder-card">
          <span className="dashboard-summary-label">Assigned Patients</span>
          <p>
            Assigned patient list will appear here once the patient assignment
            API is added.
          </p>
        </article>

        <article className="dashboard-summary-card selected-patient-card">
          <span className="dashboard-summary-label">Selected patient summary</span>
          <strong className="selected-patient-name">Demo Patient</strong>
          <span className="dashboard-summary-detail">
            Demo clinician requests are scoped through backend authorization.
          </span>
          <div className="patient-card-actions">
            <Link className="button-link secondary" to="/reports">
              Patient reports
            </Link>
            <Link className="button-link secondary" to="/trends">
              Patient trends
            </Link>
          </div>
        </article>
      </div>
    </section>
  );
}

export default PatientsPage;

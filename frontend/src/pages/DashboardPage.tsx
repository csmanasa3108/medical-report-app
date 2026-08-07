import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  clearSelectedAssignedPatientId,
  formatLoadErrorMessage,
  getAssignedPatients,
  getReports,
  getSelectedAssignedPatientId,
  setSelectedAssignedPatientId
} from "../api/client";
import type { AssignedPatientResponse, DevUser } from "../api/client";

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

function formatOptionalDate(value: string | null | undefined) {
  if (!value) {
    return "No report date";
  }

  const date = new Date(`${value}T00:00:00`);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric"
  }).format(date);
}

function formatReportCount(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "Reports unavailable";
  }

  return `${value.toLocaleString()} ${value === 1 ? "report" : "reports"}`;
}

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

function ClinicianDashboard() {
  const [assignedPatients, setAssignedPatients] = useState<
    AssignedPatientResponse[]
  >([]);
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(
    getSelectedAssignedPatientId
  );
  const [isLoadingPatients, setIsLoadingPatients] = useState(true);
  const [patientsErrorMessage, setPatientsErrorMessage] = useState("");

  useEffect(() => {
    let isCurrent = true;

    setIsLoadingPatients(true);
    setPatientsErrorMessage("");

    getAssignedPatients()
      .then((patients) => {
        if (!isCurrent) {
          return;
        }

        setAssignedPatients(patients);

        const storedPatientId = getSelectedAssignedPatientId();
        const storedPatientExists = patients.some(
          (patient) => patient.patientId === storedPatientId
        );
        const nextSelectedPatientId =
          patients.length === 1
            ? patients[0].patientId
            : storedPatientExists
              ? storedPatientId
              : null;

        setSelectedPatientId(nextSelectedPatientId);

        if (nextSelectedPatientId) {
          setSelectedAssignedPatientId(nextSelectedPatientId);
        } else {
          clearSelectedAssignedPatientId();
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setPatientsErrorMessage(
          formatLoadErrorMessage(error, "Unable to load assigned patients.")
        );
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoadingPatients(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, []);

  const selectedPatient =
    assignedPatients.find((patient) => patient.patientId === selectedPatientId) ??
    null;

  function handleSelectPatient(patientId: string) {
    setSelectedPatientId(patientId);
    setSelectedAssignedPatientId(patientId);
  }

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

      <div
        className="dashboard-summary-grid clinician-summary-grid"
        aria-label="Clinician dashboard summary"
      >
        <article className="dashboard-summary-card">
          <span className="dashboard-summary-label">Selected patient summary</span>
          {selectedPatient ? (
            <>
              <strong className="selected-patient-name">
                {selectedPatient.displayName}
              </strong>
              <span className="dashboard-summary-detail">
                {selectedPatient.email}
              </span>
              <span className="dashboard-summary-detail">
                {formatReportCount(selectedPatient.reportCount)}
              </span>
              <span className="dashboard-summary-detail">
                Latest report: {formatOptionalDate(selectedPatient.latestReportDate)}
              </span>
            </>
          ) : (
            <span className="dashboard-summary-detail">
              Select an assigned patient first.
            </span>
          )}
        </article>
        <section className="assigned-patient-panel" aria-label="Assigned patients">
          <span className="dashboard-summary-label">Assigned Patients</span>
          {isLoadingPatients ? (
            <p className="status-message">Loading assigned patients...</p>
          ) : null}
          {!isLoadingPatients && patientsErrorMessage ? (
            <p className="status-message error-message" role="alert">
              {patientsErrorMessage}
            </p>
          ) : null}
          {!isLoadingPatients &&
          !patientsErrorMessage &&
          assignedPatients.length === 0 ? (
            <p className="status-message">No assigned patients yet.</p>
          ) : null}
          {!isLoadingPatients &&
          !patientsErrorMessage &&
          assignedPatients.length > 0 ? (
            <div className="assigned-patient-list">
              {assignedPatients.map((patient) => (
                <button
                  className={
                    patient.patientId === selectedPatientId
                      ? "assigned-patient-card selected"
                      : "assigned-patient-card"
                  }
                  key={patient.patientId}
                  type="button"
                  onClick={() => handleSelectPatient(patient.patientId)}
                >
                  <span className="assigned-patient-name">
                    {patient.displayName}
                  </span>
                  <span>{patient.email}</span>
                  <span>{formatReportCount(patient.reportCount)}</span>
                  <span>
                    Latest report: {formatOptionalDate(patient.latestReportDate)}
                  </span>
                </button>
              ))}
            </div>
          ) : null}
        </section>
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

    if (devUser.role !== "PATIENT") {
      setIsLoadingReportCount(false);
      return () => {
        isCurrent = false;
      };
    }

    setIsLoadingReportCount(true);
    setReportCountError("");

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
  }, [devUser.role]);

  const reportCountValue = isLoadingReportCount
    ? "Loading"
    : reportCountError
      ? "Unavailable"
      : reportCount?.toLocaleString() ?? "Unavailable";

  if (devUser.role === "CLINICIAN") {
    return <ClinicianDashboard />;
  }

  return (
    <PatientDashboard
      reportCountValue={reportCountValue}
      reportCountError={reportCountError}
    />
  );
}

export default DashboardPage;

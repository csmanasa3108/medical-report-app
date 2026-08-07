import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  clearSelectedAssignedPatientId,
  formatLoadErrorMessage,
  getAssignedPatients,
  getSelectedAssignedPatientId,
  setSelectedAssignedPatientId
} from "../api/client";
import type { AssignedPatientResponse, DevUser } from "../api/client";

type PatientsPageProps = {
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

function PatientsPage({ devUser }: PatientsPageProps) {
  const [assignedPatients, setAssignedPatients] = useState<
    AssignedPatientResponse[]
  >([]);
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(
    getSelectedAssignedPatientId
  );
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    let isCurrent = true;

    if (devUser.role !== "CLINICIAN") {
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

    setIsLoading(true);
    setErrorMessage("");

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

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load assigned patients.")
        );
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoading(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [devUser.role]);

  function handleSelectPatient(patientId: string) {
    setSelectedPatientId(patientId);
    setSelectedAssignedPatientId(patientId);
  }

  const selectedPatient =
    assignedPatients.find((patient) => patient.patientId === selectedPatientId) ??
    null;

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

      {isLoading ? (
        <p className="status-message">Loading assigned patients...</p>
      ) : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && assignedPatients.length === 0 ? (
        <p className="status-message">No assigned patients yet.</p>
      ) : null}

      {!isLoading && !errorMessage && assignedPatients.length > 0 ? (
        <div className="patients-layout">
          <section className="assigned-patient-panel" aria-label="Assigned patients">
            <span className="dashboard-summary-label">Assigned Patients</span>
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
          </section>

          <article className="dashboard-summary-card selected-patient-card">
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
                <div className="patient-card-actions">
                  <Link className="button-link secondary" to="/reports">
                    Patient reports
                  </Link>
                  <Link className="button-link secondary" to="/trends">
                    Patient trends
                  </Link>
                </div>
              </>
            ) : (
              <span className="dashboard-summary-detail">
                Select an assigned patient first.
              </span>
            )}
          </article>
        </div>
      ) : null}
    </section>
  );
}

export default PatientsPage;

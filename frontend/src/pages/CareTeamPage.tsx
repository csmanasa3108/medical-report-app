import { FormEvent, useEffect, useState } from "react";
import {
  formatLoadErrorMessage,
  getPatientClinicianAccess,
  grantClinicianAccess,
  revokeClinicianAccess
} from "../api/client";
import type { DevUser, PatientClinicianAccessResponse } from "../api/client";
import StatusBadge from "../components/StatusBadge";

type CareTeamPageProps = {
  devUser: DevUser;
};

function formatDateTime(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit"
  }).format(date);
}

function isActiveAccess(status: string) {
  return status === "ACTIVE";
}

function CareTeamPage({ devUser }: CareTeamPageProps) {
  const [clinicianAccess, setClinicianAccess] = useState<
    PatientClinicianAccessResponse[]
  >([]);
  const [clinicianEmail, setClinicianEmail] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [revokingAccessId, setRevokingAccessId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const isPatient = devUser.role === "PATIENT";

  useEffect(() => {
    let isCurrent = true;

    if (!isPatient) {
      setClinicianAccess([]);
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

    setIsLoading(true);
    setErrorMessage("");
    setSuccessMessage("");

    getPatientClinicianAccess()
      .then((accessRows) => {
        if (isCurrent) {
          setClinicianAccess(accessRows);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load clinician access.")
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
  }, [isPatient]);

  async function refreshAccessList() {
    const accessRows = await getPatientClinicianAccess();
    setClinicianAccess(accessRows);
  }

  async function handleGrantAccess(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");
    setSuccessMessage("");

    const trimmedEmail = clinicianEmail.trim();
    if (!trimmedEmail) {
      setErrorMessage("Enter a clinician email address.");
      return;
    }

    setIsSubmitting(true);

    try {
      const access = await grantClinicianAccess({
        clinicianEmail: trimmedEmail
      });
      await refreshAccessList();
      setClinicianEmail("");
      setSuccessMessage(
        `Access granted to ${access.clinicianDisplayName}.`
      );
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to grant clinician access."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRevokeAccess(access: PatientClinicianAccessResponse) {
    const confirmed = window.confirm("Revoke this clinician's access?");
    if (!confirmed) {
      return;
    }

    setRevokingAccessId(access.accessId);
    setErrorMessage("");
    setSuccessMessage("");

    try {
      await revokeClinicianAccess(access.accessId);
      await refreshAccessList();
      setSuccessMessage(
        `Access revoked for ${access.clinicianDisplayName}.`
      );
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to revoke clinician access."
      );
    } finally {
      setRevokingAccessId(null);
    }
  }

  if (!isPatient) {
    return (
      <section className="page-section">
        <p className="eyebrow">Care Team</p>
        <h2 className="page-title">Not allowed</h2>
        <p className="page-description">
          Care Team sharing controls are available only in the patient
          development view.
        </p>
      </section>
    );
  }

  return (
    <section className="page-section care-team-page">
      <div className="section-header">
        <div>
          <p className="eyebrow">Sharing</p>
          <h2 className="page-title">Care Team</h2>
          <p className="page-description">
            Manage which clinicians can view your reports and trends.
          </p>
        </div>
      </div>

      <div className="care-team-layout">
        <section className="form-card care-team-form-card">
          <h3>Grant clinician access</h3>
          <form className="metadata-form care-team-form" onSubmit={handleGrantAccess}>
            <label>
              Clinician email
              <input
                type="email"
                value={clinicianEmail}
                onChange={(event) => setClinicianEmail(event.target.value)}
                placeholder="clinician.demo@soverahealth.local"
                disabled={isSubmitting}
                required
              />
            </label>
            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Granting..." : "Grant access"}
            </button>
          </form>
        </section>

        <section className="care-team-list-section">
          <div className="care-team-list-header">
            <h3>Clinician access</h3>
            <span className="dashboard-summary-detail">
              {clinicianAccess.length.toLocaleString()} total
            </span>
          </div>

          {isLoading ? (
            <p className="status-message">Loading clinician access...</p>
          ) : null}

          {!isLoading && successMessage ? (
            <p className="status-message success-message" role="status">
              {successMessage}
            </p>
          ) : null}

          {!isLoading && errorMessage ? (
            <p className="status-message error-message" role="alert">
              {errorMessage}
            </p>
          ) : null}

          {!isLoading && !errorMessage && clinicianAccess.length === 0 ? (
            <p className="status-message">No clinicians have been added yet.</p>
          ) : null}

          {!isLoading && clinicianAccess.length > 0 ? (
            <div className="table-scroll">
              <table className="reports-table care-team-table">
                <thead>
                  <tr>
                    <th>Clinician</th>
                    <th>Email</th>
                    <th>Status</th>
                    <th>Added</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {clinicianAccess.map((access) => (
                    <tr key={access.accessId}>
                      <td className="report-filename">
                        {access.clinicianDisplayName}
                      </td>
                      <td>{access.clinicianEmail}</td>
                      <td>
                        <StatusBadge status={access.status} />
                      </td>
                      <td>{formatDateTime(access.createdAt)}</td>
                      <td>
                        {isActiveAccess(access.status) ? (
                          <button
                            className="action-button secondary danger table-action-button"
                            type="button"
                            disabled={revokingAccessId === access.accessId}
                            onClick={() => handleRevokeAccess(access)}
                          >
                            {revokingAccessId === access.accessId
                              ? "Revoking..."
                              : "Revoke"}
                          </button>
                        ) : (
                          <span className="dashboard-summary-detail">
                            No action
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </section>
      </div>
    </section>
  );
}

export default CareTeamPage;

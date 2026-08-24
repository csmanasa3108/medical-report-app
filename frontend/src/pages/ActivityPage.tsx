import { useEffect, useState } from "react";
import {
  formatLoadErrorMessage,
  getSelectedAssignedPatientId
} from "../api/client";
import type { DevUser } from "../api/client";
import { getPatientVaultService } from "../vault";
import { getPatientVaultMode } from "../vault/config";
import type { VaultAuditEvent } from "../vault";

type ActivityPageProps = {
  devUser: DevUser;
};

const ACTION_LABELS: Record<string, string> = {
  CLINICIAN_ACCESS_GRANTED: "Clinician access granted",
  CLINICIAN_ACCESS_REVOKED: "Clinician access revoked",
  REPORT_UPLOADED: "Report uploaded",
  PARSED_OBSERVATION_CONFIRMED: "Observation confirmed",
  REPORT_DELETED: "Report deleted"
};

function formatLabel(value: string | null) {
  if (!value) {
    return "Not provided";
  }

  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function formatAction(action: string) {
  return ACTION_LABELS[action] ?? formatLabel(action);
}

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

function ActivityPage({ devUser }: ActivityPageProps) {
  const [events, setEvents] = useState<VaultAuditEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const isClinician = devUser.role === "CLINICIAN";
  const selectedPatientId = isClinician ? getSelectedAssignedPatientId() : null;
  const isLocalVaultMode = getPatientVaultMode() === "local";

  useEffect(() => {
    let isCurrent = true;

    setIsLoading(true);
    setErrorMessage("");
    setEvents([]);

    if (isClinician && !selectedPatientId) {
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

    getPatientVaultService()
      .listAuditEvents({ patientId: selectedPatientId })
      .then((activityEvents) => {
        if (isCurrent) {
          setEvents(activityEvents);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load activity.")
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
  }, [isClinician, selectedPatientId]);

  return (
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Audit history</p>
          <h2 className="page-title">Activity</h2>
          <p className="page-description">
            Review recent access and report activity.
          </p>
        </div>
      </div>

      {isLoading ? <p className="status-message">Loading activity...</p> : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && isClinician && !selectedPatientId ? (
        <p className="status-message">Select a patient to view activity.</p>
      ) : null}

      {!isLoading &&
      !errorMessage &&
      (!isClinician || selectedPatientId) &&
      events.length === 0 ? (
        <p className="status-message">
          {isLocalVaultMode
            ? "Local activity history will be available as vault features are completed."
            : "No activity yet."}
        </p>
      ) : null}

      {!isLoading && !errorMessage && events.length > 0 ? (
        <div className="table-scroll">
          <table className="reports-table">
            <thead>
              <tr>
                <th>Action</th>
                <th>Actor role</th>
                <th>Resource</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.auditEventId}>
                  <td className="report-filename">
                    {formatAction(event.action)}
                  </td>
                  <td>{formatLabel(event.actorRole)}</td>
                  <td>{formatLabel(event.resourceTypeName)}</td>
                  <td>{formatDateTime(event.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

export default ActivityPage;

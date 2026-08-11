import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  confirmParsedObservation,
  formatLoadErrorMessage,
  getParsedObservationReviewQueue,
  getSelectedAssignedPatientId,
  rejectParsedObservation
} from "../api/client";
import type { DevUser, ParsedObservationReviewResponse } from "../api/client";

type ReviewQueuePageProps = {
  devUser: DevUser;
};

function formatDate(value: string | null) {
  if (!value) {
    return "Not provided";
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

function formatValue(observation: ParsedObservationReviewResponse) {
  if (observation.valueText) {
    return observation.valueText;
  }

  if (observation.numericValue === null) {
    return "Not provided";
  }

  const value = observation.numericValue.toLocaleString(undefined, {
    maximumFractionDigits: 4
  });
  return observation.unit ? `${value} ${observation.unit}` : value;
}

function formatOptionalValue(value: string | null) {
  return value?.trim() ? value : "Not provided";
}

function formatFlag(flag: string | null) {
  if (!flag) {
    return "unknown";
  }

  return flag.replace(/_/g, " ").toLowerCase();
}

function getFlagClass(flag: string | null) {
  switch (flag?.toLowerCase()) {
    case "normal":
      return "status-badge-confirmed";
    case "abnormal":
    case "high":
    case "low":
      return "status-badge-review";
    default:
      return "status-badge-neutral";
  }
}

function ReviewQueuePage({ devUser }: ReviewQueuePageProps) {
  const [observations, setObservations] = useState<
    ParsedObservationReviewResponse[]
  >([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [runningActionId, setRunningActionId] = useState<string | null>(null);

  const isClinician = devUser.role === "CLINICIAN";
  const isPatient = devUser.role === "PATIENT";
  const selectedPatientId = isClinician ? getSelectedAssignedPatientId() : null;

  const loadReviewQueue = useCallback(() => {
    let isCurrent = true;

    setIsLoading(true);
    setErrorMessage("");
    setObservations([]);

    if (isClinician && !selectedPatientId) {
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

    getParsedObservationReviewQueue(selectedPatientId)
      .then((reviewQueue) => {
        if (isCurrent) {
          setObservations(reviewQueue);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load review queue.")
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

  useEffect(() => loadReviewQueue(), [loadReviewQueue]);

  async function refreshReviewQueue() {
    const reviewQueue = await getParsedObservationReviewQueue(selectedPatientId);
    setObservations(reviewQueue);
  }

  async function handleConfirm(parsedObservationId: string) {
    setRunningActionId(parsedObservationId);
    setErrorMessage("");
    setSuccessMessage("");

    try {
      await confirmParsedObservation(parsedObservationId);
      await refreshReviewQueue();
      setSuccessMessage("Result confirmed.");
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to confirm result."
      );
    } finally {
      setRunningActionId(null);
    }
  }

  async function handleReject(parsedObservationId: string) {
    setRunningActionId(parsedObservationId);
    setErrorMessage("");
    setSuccessMessage("");

    try {
      await rejectParsedObservation(parsedObservationId);
      await refreshReviewQueue();
      setSuccessMessage("Result rejected.");
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to reject result."
      );
    } finally {
      setRunningActionId(null);
    }
  }

  return (
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Parsed results</p>
          <h2 className="page-title">Review Queue</h2>
          <p className="page-description">
            Review extracted results before adding them to trends.
          </p>
        </div>
      </div>

      {isLoading ? (
        <p className="status-message">Loading review queue...</p>
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

      {!isLoading && !errorMessage && isClinician && !selectedPatientId ? (
        <p className="status-message">
          Select a patient to view their review queue.
        </p>
      ) : null}

      {!isLoading &&
      !errorMessage &&
      (!isClinician || selectedPatientId) &&
      observations.length === 0 ? (
        <p className="status-message">No results waiting for review.</p>
      ) : null}

      {!isLoading && !errorMessage && observations.length > 0 ? (
        <div className="table-scroll">
          <table className="reports-table review-queue-table">
            <thead>
              <tr>
                <th>Test</th>
                <th>Value</th>
                <th>Observed</th>
                <th>Reference</th>
                <th>Flag</th>
                <th>Report</th>
                <th>Lab</th>
                <th>Report date</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {observations.map((observation) => {
                const isActionRunning =
                  runningActionId === observation.parsedObservationId;
                const isAnyActionRunning = runningActionId !== null;
                const isReviewable = observation.status === "NEEDS_REVIEW";

                return (
                  <tr key={observation.parsedObservationId}>
                    <td className="report-filename">
                      {formatOptionalValue(observation.testName)}
                    </td>
                    <td>{formatValue(observation)}</td>
                    <td>{formatDate(observation.observedAt)}</td>
                    <td>{formatOptionalValue(observation.referenceRange)}</td>
                    <td>
                      <span
                        className={`status-badge ${getFlagClass(
                          observation.abnormalFlag
                        )}`}
                      >
                        {formatFlag(observation.abnormalFlag)}
                      </span>
                    </td>
                    <td>{observation.reportOriginalFilename}</td>
                    <td>{formatOptionalValue(observation.labName)}</td>
                    <td>{formatDate(observation.reportDate)}</td>
                    <td>
                      <div className="table-action-group">
                        <Link
                          className="table-detail-link"
                          to={`/reports/${observation.reportId}`}
                        >
                          View report
                        </Link>
                        {isPatient && isReviewable ? (
                          <>
                            <button
                              className="action-button table-action-button"
                              type="button"
                              disabled={isAnyActionRunning}
                              onClick={() =>
                                handleConfirm(
                                  observation.parsedObservationId
                                )
                              }
                            >
                              {isActionRunning ? "Working..." : "Confirm"}
                            </button>
                            <button
                              className="action-button secondary danger table-action-button"
                              type="button"
                              disabled={isAnyActionRunning}
                              onClick={() =>
                                handleReject(observation.parsedObservationId)
                              }
                            >
                              Reject
                            </button>
                          </>
                        ) : null}
                        {!isPatient ? (
                          <span className="dashboard-summary-detail">
                            View only
                          </span>
                        ) : null}
                        {isPatient && !isReviewable ? (
                          <span className="dashboard-summary-detail">
                            No action
                          </span>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

export default ReviewQueuePage;

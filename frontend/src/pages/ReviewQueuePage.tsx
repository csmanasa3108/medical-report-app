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

type ReviewStatus = "NEEDS_REVIEW" | "CONFIRMED" | "REJECTED";

const REVIEW_STATUS_OPTIONS: Array<{ label: string; value: ReviewStatus }> = [
  { label: "Needs Review", value: "NEEDS_REVIEW" },
  { label: "Confirmed", value: "CONFIRMED" },
  { label: "Rejected", value: "REJECTED" }
];

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

function formatExtractedValue(observation: ParsedObservationReviewResponse) {
  if (observation.valueText?.trim()) {
    return observation.valueText;
  }

  if (observation.numericValue === null) {
    return "Not provided";
  }

  return observation.numericValue.toLocaleString(undefined, {
    maximumFractionDigits: 4
  });
}

function formatOptionalValue(value: string | null) {
  return value?.trim() ? value : "Not provided";
}

function formatFlag(flag: string | null) {
  if (!flag) {
    return "";
  }

  return flag.replace(/_/g, " ").toLowerCase();
}

function formatStatus(status: string) {
  return status
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
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

function getStatusClass(status: string) {
  switch (status) {
    case "NEEDS_REVIEW":
      return "status-badge-review";
    case "CONFIRMED":
      return "status-badge-confirmed";
    case "REJECTED":
      return "status-badge-rejected";
    default:
      return "status-badge-neutral";
  }
}

function getCountLabel(count: number, status: ReviewStatus, isLoading: boolean) {
  if (isLoading) {
    return "Loading results";
  }

  const formattedCount = count.toLocaleString();

  switch (status) {
    case "CONFIRMED":
      return `${formattedCount} confirmed ${count === 1 ? "result" : "results"}`;
    case "REJECTED":
      return `${formattedCount} rejected ${count === 1 ? "result" : "results"}`;
    case "NEEDS_REVIEW":
    default:
      return `${formattedCount} ${count === 1 ? "result" : "results"} waiting`;
  }
}

function ReviewEmptyState({
  isClinician,
  status
}: {
  isClinician: boolean;
  status: ReviewStatus;
}) {
  const isNeedsReview = status === "NEEDS_REVIEW";

  return (
    <section className="review-empty-state">
      <div>
        <p className="eyebrow">Worklist</p>
        <h3>
          {isNeedsReview
            ? "No results waiting for review"
            : `No ${formatStatus(status).toLowerCase()} results`}
        </h3>
        <p>
          {isNeedsReview
            ? "Upload a report to extract results, or check confirmed results in Trends."
            : "Use the status filters to review another part of the worklist."}
        </p>
      </div>
      <div className="review-empty-actions">
        {isClinician ? (
          <Link className="button-link" to="/reports">
            View Patient Reports
          </Link>
        ) : (
          <Link className="button-link" to="/upload">
            Upload Report
          </Link>
        )}
        <Link className="button-link secondary" to="/trends">
          View Trends
        </Link>
      </div>
    </section>
  );
}

function SelectPatientEmptyState() {
  return (
    <section className="review-empty-state">
      <div>
        <p className="eyebrow">Patient required</p>
        <h3>Select a patient to view their review queue</h3>
        <p>
          Choose an assigned patient before opening parsed observations,
          reports, trends, or activity.
        </p>
      </div>
      <div className="review-empty-actions">
        <Link className="button-link" to="/patients">
          View Assigned Patients
        </Link>
      </div>
    </section>
  );
}

function ReviewObservationCard({
  isAnyActionRunning,
  isPatient,
  observation,
  onConfirm,
  onReject,
  runningActionId
}: {
  isAnyActionRunning: boolean;
  isPatient: boolean;
  observation: ParsedObservationReviewResponse;
  onConfirm: (parsedObservationId: string) => void;
  onReject: (parsedObservationId: string) => void;
  runningActionId: string | null;
}) {
  const isActionRunning = runningActionId === observation.parsedObservationId;
  const isReviewable = observation.status === "NEEDS_REVIEW";

  return (
    <article className="review-item-card">
      <div className="review-item-main">
        <div className="review-item-title-row">
          <div>
            <p className="eyebrow">Parsed result</p>
            <h3>{formatOptionalValue(observation.testName)}</h3>
          </div>
          <span className={`status-badge ${getStatusClass(observation.status)}`}>
            {formatStatus(observation.status)}
          </span>
        </div>

        <dl className="review-item-details">
          <div>
            <dt>Raw extracted value</dt>
            <dd>{formatExtractedValue(observation)}</dd>
          </div>
          <div>
            <dt>Unit</dt>
            <dd>{formatOptionalValue(observation.unit)}</dd>
          </div>
          <div>
            <dt>Observed date</dt>
            <dd>{formatDate(observation.observedAt)}</dd>
          </div>
          <div>
            <dt>Reference range</dt>
            <dd>{formatOptionalValue(observation.referenceRange)}</dd>
          </div>
        </dl>

        <div className="review-source-panel">
          <div>
            <span className="review-source-label">Source report</span>
            <strong>{observation.reportOriginalFilename}</strong>
          </div>
          <div>
            <span className="review-source-label">Lab</span>
            <span>{formatOptionalValue(observation.labName)}</span>
          </div>
          <div>
            <span className="review-source-label">Report date</span>
            <span>{formatDate(observation.reportDate)}</span>
          </div>
        </div>
      </div>

      <aside className="review-item-side">
        {observation.abnormalFlag ? (
          <span className={`status-badge ${getFlagClass(observation.abnormalFlag)}`}>
            {formatFlag(observation.abnormalFlag)}
          </span>
        ) : (
          <span className="status-badge status-badge-neutral">No flag</span>
        )}

        <Link className="button-link secondary" to={`/reports/${observation.reportId}`}>
          Open source report
        </Link>

        {isPatient && isReviewable ? (
          <div className="review-card-actions">
            <button
              className="action-button review-card-action-button"
              type="button"
              disabled={isAnyActionRunning}
              onClick={() => onConfirm(observation.parsedObservationId)}
            >
              {isActionRunning ? "Working..." : "Confirm"}
            </button>
            <button
              className="action-button secondary danger review-card-action-button"
              type="button"
              disabled={isAnyActionRunning}
              onClick={() => onReject(observation.parsedObservationId)}
            >
              Reject
            </button>
          </div>
        ) : null}

        {!isPatient ? (
          <span className="review-readonly-badge">View only</span>
        ) : null}

        {isPatient && !isReviewable ? (
          <span className="review-readonly-badge">Reviewed</span>
        ) : null}
      </aside>
    </article>
  );
}

function ReviewQueuePage({ devUser }: ReviewQueuePageProps) {
  const [observations, setObservations] = useState<
    ParsedObservationReviewResponse[]
  >([]);
  const [selectedStatus, setSelectedStatus] =
    useState<ReviewStatus>("NEEDS_REVIEW");
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

    getParsedObservationReviewQueue(selectedPatientId, selectedStatus)
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
  }, [isClinician, selectedPatientId, selectedStatus]);

  useEffect(() => loadReviewQueue(), [loadReviewQueue]);

  async function refreshReviewQueue() {
    const reviewQueue = await getParsedObservationReviewQueue(
      selectedPatientId,
      selectedStatus
    );
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

  const countLabel =
    isClinician && !selectedPatientId && !isLoading
      ? "Select patient"
      : getCountLabel(observations.length, selectedStatus, isLoading);
  const isAnyActionRunning = runningActionId !== null;

  return (
    <section className="review-queue-page">
      <div className="review-queue-header">
        <div>
          <p className="eyebrow">Parsed results</p>
          <h2 className="page-title">Review Queue</h2>
          <p className="page-description">
            Review extracted results before they appear in trends.
          </p>
        </div>
        <span className="review-count-pill">{countLabel}</span>
      </div>

      <div className="review-filter-bar" aria-label="Review status filter">
        {REVIEW_STATUS_OPTIONS.map((option) => (
          <button
            className={
              option.value === selectedStatus
                ? "review-filter-pill active"
                : "review-filter-pill"
            }
            key={option.value}
            type="button"
            onClick={() => {
              setSelectedStatus(option.value);
              setSuccessMessage("");
            }}
          >
            {option.label}
          </button>
        ))}
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
        <SelectPatientEmptyState />
      ) : null}

      {!isLoading &&
      !errorMessage &&
      (!isClinician || selectedPatientId) &&
      observations.length === 0 ? (
        <ReviewEmptyState isClinician={isClinician} status={selectedStatus} />
      ) : null}

      {!isLoading && !errorMessage && observations.length > 0 ? (
        <div className="review-worklist" aria-label="Parsed observation worklist">
          {observations.map((observation) => (
            <ReviewObservationCard
              isAnyActionRunning={isAnyActionRunning}
              isPatient={isPatient}
              key={observation.parsedObservationId}
              observation={observation}
              onConfirm={handleConfirm}
              onReject={handleReject}
              runningActionId={runningActionId}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}

export default ReviewQueuePage;

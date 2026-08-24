import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  formatLoadErrorMessage,
  getAssignedPatients,
  getSelectedAssignedPatientId
} from "../api/client";
import type { AssignedPatientResponse, DevUser } from "../api/client";
import StatusBadge from "../components/StatusBadge";
import { getPatientVaultService } from "../vault";
import type { VaultReportDocument } from "../vault";

type ReportsLocationState = {
  successMessage?: string;
};

type ReportsListPageProps = {
  devUser: DevUser;
};

type ReportSummaryCard = {
  label: string;
  value: string;
  detail: string;
  detailTitle?: string;
  truncateDetail?: boolean;
  compactValue?: boolean;
  to?: string;
  tone?: "primary" | "sand" | "neutral";
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

function formatDateTime(value: string | null) {
  if (!value) {
    return "Not provided";
  }

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

function getLatestReport(reports: VaultReportDocument[]) {
  return [...reports].sort((first, second) => {
    const firstTime = new Date(first.uploadedAt ?? "").getTime();
    const secondTime = new Date(second.uploadedAt ?? "").getTime();
    return secondTime - firstTime;
  })[0];
}

function buildSummaryCards({
  isLoading,
  reports,
  reviewCount,
  reviewError
}: {
  isLoading: boolean;
  reports: VaultReportDocument[];
  reviewCount: number | null;
  reviewError: string;
}): ReportSummaryCard[] {
  const latestReport = getLatestReport(reports);

  return [
    {
      label: "Total reports",
      value: isLoading ? "Loading" : reports.length.toLocaleString(),
      detail: isLoading
        ? "Loading report library..."
        : reports.length === 1
          ? "1 document in library"
          : `${reports.length.toLocaleString()} documents in library`,
      to: "/reports",
      tone: "primary"
    },
    {
      label: "Results waiting for review",
      value: isLoading
        ? "Loading"
        : reviewError
          ? "Open"
          : String(reviewCount ?? 0),
      detail: reviewError
        ? "Open the review queue"
        : reviewCount === 1
          ? "1 parsed result needs review"
          : `${(reviewCount ?? 0).toLocaleString()} parsed results need review`,
      to: "/review",
      tone: "sand"
    },
    {
      label: "Recently uploaded",
      value: isLoading ? "Loading" : latestReport ? "Latest report" : "None",
      detail: isLoading
        ? "Loading recent uploads..."
        : latestReport
          ? latestReport.originalFilename
          : "No recent uploads",
      detailTitle: latestReport?.originalFilename,
      truncateDetail: Boolean(latestReport),
      compactValue: true,
      to: latestReport ? `/reports/${latestReport.reportId}` : "/upload",
      tone: "neutral"
    },
    {
      label: "Confirmed results",
      value: "Trends",
      detail: "Review confirmed observations over time",
      to: "/trends",
      tone: "neutral"
    }
  ];
}

function ReportSummaryGrid({ cards }: { cards: ReportSummaryCard[] }) {
  return (
    <div className="reports-summary-grid" aria-label="Report workflow summary">
      {cards.map((card) => {
        const content = (
          <>
            <span className="dashboard-summary-label">{card.label}</span>
            <strong
              className={
                card.compactValue
                  ? "dashboard-summary-value reports-summary-value-compact"
                  : "dashboard-summary-value"
              }
            >
              {card.value}
            </strong>
            <span
              className={
                card.truncateDetail
                  ? "dashboard-summary-detail text-truncate"
                  : "dashboard-summary-detail"
              }
              title={card.detailTitle}
            >
              {card.detail}
            </span>
          </>
        );

        return card.to ? (
          <Link
            className={`dashboard-summary-card dashboard-summary-link dashboard-summary-${card.tone ?? "neutral"}`}
            key={card.label}
            to={card.to}
          >
            {content}
          </Link>
        ) : (
          <article
            className={`dashboard-summary-card dashboard-summary-${card.tone ?? "neutral"}`}
            key={card.label}
          >
            {content}
          </article>
        );
      })}
    </div>
  );
}

function ReportsEmptyState({ isClinician }: { isClinician: boolean }) {
  return (
    <section className="reports-empty-state">
      <div>
        <p className="eyebrow">Report library</p>
        <h3>No reports uploaded yet</h3>
        <p>
          Upload a diagnostic report to extract results and start tracking trends.
        </p>
      </div>
      {!isClinician ? (
        <Link className="button-link" to="/upload">
          Upload Report
        </Link>
      ) : null}
    </section>
  );
}

function SelectPatientState() {
  return (
    <section className="reports-empty-state">
      <div>
        <p className="eyebrow">Patient required</p>
        <h3>Select a patient to view reports.</h3>
        <p>
          Choose an assigned patient before opening their report library.
        </p>
      </div>
      <Link className="button-link" to="/patients">
        Select Patient
      </Link>
    </section>
  );
}

function ReportsListPage({ devUser }: ReportsListPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [reports, setReports] = useState<VaultReportDocument[]>([]);
  const [assignedPatients, setAssignedPatients] = useState<
    AssignedPatientResponse[]
  >([]);
  const [reviewCount, setReviewCount] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingPatientContext, setIsLoadingPatientContext] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [reviewErrorMessage, setReviewErrorMessage] = useState("");
  const [patientContextErrorMessage, setPatientContextErrorMessage] =
    useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [deletingReportId, setDeletingReportId] = useState<string | null>(null);
  const isClinician = devUser.role === "CLINICIAN";
  const selectedPatientId = isClinician ? getSelectedAssignedPatientId() : null;
  const selectedPatient =
    assignedPatients.find((patient) => patient.patientId === selectedPatientId) ??
    null;

  useEffect(() => {
    let isCurrent = true;

    setIsLoadingPatientContext(isClinician);
    setPatientContextErrorMessage("");
    setAssignedPatients([]);

    if (!isClinician) {
      setIsLoadingPatientContext(false);
      return () => {
        isCurrent = false;
      };
    }

    getAssignedPatients()
      .then((patients) => {
        if (isCurrent) {
          setAssignedPatients(patients);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setPatientContextErrorMessage(
          formatLoadErrorMessage(error, "Unable to load selected patient.")
        );
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoadingPatientContext(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [isClinician]);

  useEffect(() => {
    let isCurrent = true;

    setIsLoading(true);
    setErrorMessage("");
    setReviewErrorMessage("");
    setReports([]);
    setReviewCount(null);

    if (isClinician && !selectedPatientId) {
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

    Promise.allSettled([
      getPatientVaultService().listReports({ patientId: selectedPatientId }),
      getPatientVaultService().listParsedObservations({
        patientId: selectedPatientId,
        status: "NEEDS_REVIEW"
      })
    ])
      .then(([reportsResult, reviewResult]) => {
        if (!isCurrent) {
          return;
        }

        if (reportsResult.status === "fulfilled") {
          setReports(reportsResult.value);
        } else {
          setErrorMessage(
            formatLoadErrorMessage(reportsResult.reason, "Unable to load reports.")
          );
        }

        if (reviewResult.status === "fulfilled") {
          setReviewCount(reviewResult.value.length);
        } else {
          setReviewErrorMessage(
            formatLoadErrorMessage(
              reviewResult.reason,
              "Unable to load review queue."
            )
          );
        }
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

  useEffect(() => {
    const state = location.state as ReportsLocationState | null;

    if (!state?.successMessage) {
      return;
    }

    setSuccessMessage(state.successMessage);
    navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);

  async function handleDeleteReport(reportId: string) {
    const confirmed = window.confirm(
      "Delete this report? This cannot be undone."
    );

    if (!confirmed) {
      return;
    }

    setDeletingReportId(reportId);
    setErrorMessage("");
    setSuccessMessage("");

    try {
      await getPatientVaultService().deleteReport(reportId);
      const reportList = await getPatientVaultService().listReports({
        patientId: selectedPatientId
      });
      setReports(reportList);
      setSuccessMessage("Report deleted.");
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to delete report."
      );
    } finally {
      setDeletingReportId(null);
    }
  }

  return (
    <section className="reports-library-page">
      <div className="reports-library-header">
        <div>
          <p className="eyebrow">Report library</p>
          <h2 className="page-title">
            {isClinician ? "Patient reports" : "Reports"}
          </h2>
          <p className="page-description">
            {isClinician
              ? "View reports for the selected assigned patient."
              : "Upload and manage your diagnostic reports."}
          </p>
        </div>
        {!isClinician ? (
          <div className="reports-header-actions">
            <Link className="button-link" to="/upload">
              Upload Report
            </Link>
            <Link className="button-link secondary" to="/review">
              Review Queue
            </Link>
          </div>
        ) : null}
      </div>

      {isClinician && selectedPatientId ? (
        <section className="selected-report-patient-card">
          <span className="dashboard-summary-label">Selected patient</span>
          {isLoadingPatientContext ? (
            <strong>Loading patient...</strong>
          ) : selectedPatient ? (
            <>
              <strong>{selectedPatient.displayName}</strong>
              <span>{selectedPatient.email}</span>
            </>
          ) : (
            <>
              <strong>Selected patient</strong>
              <span>{selectedPatientId}</span>
            </>
          )}
        </section>
      ) : null}

      {!isLoadingPatientContext && patientContextErrorMessage ? (
        <p className="status-message error-message" role="alert">
          {patientContextErrorMessage}
        </p>
      ) : null}

      {isClinician && !selectedPatientId ? <SelectPatientState /> : null}

      {!isClinician || selectedPatientId ? (
        <>
          <ReportSummaryGrid
            cards={buildSummaryCards({
              isLoading,
              reports,
              reviewCount,
              reviewError: reviewErrorMessage
            })}
          />

          {isLoading ? (
            <p className="status-message">Loading reports...</p>
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

          {!isLoading && !errorMessage && reviewErrorMessage ? (
            <p className="status-message error-message" role="alert">
              {reviewErrorMessage}
            </p>
          ) : null}

          {!isLoading && !errorMessage && reports.length === 0 ? (
            <ReportsEmptyState isClinician={isClinician} />
          ) : null}

          {!isLoading && !errorMessage && reports.length > 0 ? (
            <section className="reports-list-card">
              <div className="reports-list-header">
                <div>
                  <p className="eyebrow">Documents</p>
                  <h3>Report library</h3>
                </div>
                <span className="review-count-pill">
                  {reports.length.toLocaleString()}{" "}
                  {reports.length === 1 ? "report" : "reports"}
                </span>
              </div>

              <div className="table-scroll reports-library-scroll">
                <table className="reports-table reports-library-table">
                  <thead>
                    <tr>
                      <th>Report</th>
                      <th>Lab</th>
                      <th>Report date</th>
                      <th>Uploaded</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reports.map((report) => (
                      <tr key={report.reportId}>
                        <td className="report-filename">
                          <Link
                            className="report-title-link"
                            title={report.originalFilename}
                            to={`/reports/${report.reportId}`}
                          >
                            {report.originalFilename}
                          </Link>
                        </td>
                        <td>{report.labName || "Not provided"}</td>
                        <td>{formatDate(report.reportDate)}</td>
                        <td>{formatDateTime(report.uploadedAt)}</td>
                        <td>
                          <StatusBadge status={report.status} />
                        </td>
                        <td>
                          <div className="reports-action-group">
                            <Link
                              className="button-link secondary table-action-button"
                              to={`/reports/${report.reportId}`}
                            >
                              Open
                            </Link>
                            <Link
                              className="button-link secondary table-action-button"
                              to="/review"
                            >
                              Review extracted results
                            </Link>
                            {!isClinician ? (
                              <button
                                className="action-button secondary danger table-action-button"
                                type="button"
                                onClick={() => handleDeleteReport(report.reportId)}
                                disabled={deletingReportId !== null}
                              >
                                {deletingReportId === report.reportId
                                  ? "Deleting..."
                                  : "Delete"}
                              </button>
                            ) : (
                              <span className="review-readonly-badge">
                                View only
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

export default ReportsListPage;

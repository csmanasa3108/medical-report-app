import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  deleteReport,
  formatLoadErrorMessage,
  getReports,
  ReportResponse
} from "../api/client";
import type { DevUser } from "../api/client";
import StatusBadge from "../components/StatusBadge";

type ReportsLocationState = {
  successMessage?: string;
};

type ReportsListPageProps = {
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

function ReportsListPage({ devUser }: ReportsListPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [reports, setReports] = useState<ReportResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [deletingReportId, setDeletingReportId] = useState<string | null>(null);
  const isClinician = devUser.role === "CLINICIAN";

  useEffect(() => {
    let isCurrent = true;

    getReports()
      .then((reportList) => {
        if (isCurrent) {
          setReports(reportList);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load reports.")
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
  }, []);

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
      await deleteReport(reportId);
      const reportList = await getReports();
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
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Reports</p>
          <h2 className="page-title">
            {isClinician ? "Patient reports" : "Your reports"}
          </h2>
          <p className="page-description">
            {isClinician
              ? "View diagnostic reports for the selected assigned patient."
              : "Manage uploaded diagnostic reports and review extracted lab observations."}
          </p>
        </div>
        {!isClinician ? (
          <Link className="button-link" to="/reports/new">
            Upload Report
          </Link>
        ) : null}
      </div>

      {isLoading ? <p className="status-message">Loading reports...</p> : null}

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

      {!isLoading && !errorMessage && reports.length === 0 ? (
        <p className="status-message">No reports have been created yet.</p>
      ) : null}

      {!isLoading && reports.length > 0 ? (
        <div className="table-scroll">
          <table className="reports-table">
            <thead>
              <tr>
                <th>Filename</th>
                <th>Report date</th>
                <th>Lab</th>
                <th>Status</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {reports.map((report) => (
                <tr key={report.id}>
                  <td className="report-filename">{report.originalFilename}</td>
                  <td>{formatDate(report.reportDate)}</td>
                  <td>{report.labName || "Not provided"}</td>
                  <td>
                    <StatusBadge status={report.status} />
                  </td>
                  <td>{formatDateTime(report.createdAt)}</td>
                  <td>
                    <div className="table-action-group">
                      <Link
                        className="table-detail-link"
                        to={`/reports/${report.id}`}
                      >
                        View details
                      </Link>
                      {!isClinician ? (
                        <button
                          className="action-button secondary danger table-action-button"
                          type="button"
                          onClick={() => handleDeleteReport(report.id)}
                          disabled={deletingReportId !== null}
                        >
                          {deletingReportId === report.id ? "Deleting..." : "Delete"}
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

export default ReportsListPage;

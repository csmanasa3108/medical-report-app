import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getReports, ReportResponse } from "../api/client";

function formatDate(value: string) {
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

function ReportsListPage() {
  const [reports, setReports] = useState<ReportResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

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
          error instanceof Error ? error.message : "Unable to load reports."
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

  return (
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Reports</p>
          <h2>Report Metadata</h2>
        </div>
        <Link className="button-link" to="/reports/new">
          New Report
        </Link>
      </div>

      {isLoading ? <p className="status-message">Loading reports...</p> : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && reports.length === 0 ? (
        <p className="status-message">No reports have been created yet.</p>
      ) : null}

      {!isLoading && !errorMessage && reports.length > 0 ? (
        <div className="table-scroll">
          <table className="reports-table">
            <thead>
              <tr>
                <th>Filename</th>
                <th>Report date</th>
                <th>Lab</th>
                <th>Status</th>
                <th>Created</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              {reports.map((report) => (
                <tr key={report.id}>
                  <td>{report.originalFilename}</td>
                  <td>{formatDate(report.reportDate)}</td>
                  <td>{report.labName}</td>
                  <td>{report.status}</td>
                  <td>{formatDateTime(report.createdAt)}</td>
                  <td>
                    <Link to={`/reports/${report.id}`}>View</Link>
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

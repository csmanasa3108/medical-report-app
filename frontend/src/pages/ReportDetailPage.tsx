import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getReport, ReportResponse } from "../api/client";

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

function ReportDetailPage() {
  const { reportId } = useParams();
  const [report, setReport] = useState<ReportResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    let isCurrent = true;

    if (!reportId) {
      setReport(null);
      setErrorMessage("No report was selected.");
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage("");

    getReport(reportId)
      .then((reportResponse) => {
        if (isCurrent) {
          setReport(reportResponse);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          error instanceof Error ? error.message : "Unable to load the report."
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
  }, [reportId]);

  return (
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Reports</p>
          <h2>Report Detail</h2>
        </div>
        <Link className="button-link secondary" to="/reports">
          All Reports
        </Link>
      </div>

      {isLoading ? <p className="status-message">Loading report...</p> : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && report ? (
        <dl className="detail-list">
          <div>
            <dt>Original filename</dt>
            <dd>{report.originalFilename}</dd>
          </div>
          <div>
            <dt>Report date</dt>
            <dd>{formatDate(report.reportDate)}</dd>
          </div>
          <div>
            <dt>Lab name</dt>
            <dd>{report.labName || "Not provided"}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{report.status}</dd>
          </div>
          <div>
            <dt>Created</dt>
            <dd>{formatDateTime(report.createdAt)}</dd>
          </div>
        </dl>
      ) : null}
    </section>
  );
}

export default ReportDetailPage;

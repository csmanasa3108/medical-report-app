import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  extractReportText,
  getParsedObservations,
  getReport,
  parseReportObservations,
  ParsedObservationResponse,
  ReportResponse
} from "../api/client";

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

function formatOptionalValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === "") {
    return "Not provided";
  }

  return value;
}

function ReportDetailPage() {
  const { reportId } = useParams();
  const [report, setReport] = useState<ReportResponse | null>(null);
  const [parsedObservations, setParsedObservations] = useState<
    ParsedObservationResponse[]
  >([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isParsedLoading, setIsParsedLoading] = useState(false);
  const [activeAction, setActiveAction] = useState<
    "extract" | "parse" | "refresh" | null
  >(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [parsedErrorMessage, setParsedErrorMessage] = useState("");
  const [actionMessage, setActionMessage] = useState("");

  useEffect(() => {
    let isCurrent = true;

    if (!reportId) {
      setReport(null);
      setParsedObservations([]);
      setErrorMessage("No report was selected.");
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage("");
    setParsedErrorMessage("");
    setActionMessage("");

    Promise.all([getReport(reportId), getParsedObservations(reportId)])
      .then(([reportResponse, parsedObservationList]) => {
        if (!isCurrent) {
          return;
        }

        setReport(reportResponse);
        setParsedObservations(parsedObservationList);
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

  async function refreshReportAndParsedObservations() {
    if (!reportId) {
      return;
    }

    const [reportResponse, parsedObservationList] = await Promise.all([
      getReport(reportId),
      getParsedObservations(reportId)
    ]);

    setReport(reportResponse);
    setParsedObservations(parsedObservationList);
  }

  async function handleExtractText() {
    if (!reportId) {
      return;
    }

    setActiveAction("extract");
    setParsedErrorMessage("");
    setActionMessage("");

    try {
      await extractReportText(reportId);
      await refreshReportAndParsedObservations();
      setActionMessage("Text extraction completed.");
    } catch (error: unknown) {
      setParsedErrorMessage(
        error instanceof Error ? error.message : "Unable to extract report text."
      );
    } finally {
      setActiveAction(null);
    }
  }

  async function handleParseObservations() {
    if (!reportId) {
      return;
    }

    setActiveAction("parse");
    setParsedErrorMessage("");
    setActionMessage("");

    try {
      await parseReportObservations(reportId);
      await refreshReportAndParsedObservations();
      setActionMessage("Observation parsing completed.");
    } catch (error: unknown) {
      setParsedErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to parse observations."
      );
    } finally {
      setActiveAction(null);
    }
  }

  async function handleRefreshParsedObservations() {
    setActiveAction("refresh");
    setIsParsedLoading(true);
    setParsedErrorMessage("");
    setActionMessage("");

    try {
      await refreshReportAndParsedObservations();
      setActionMessage("Parsed observations refreshed.");
    } catch (error: unknown) {
      setParsedErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to refresh parsed observations."
      );
    } finally {
      setIsParsedLoading(false);
      setActiveAction(null);
    }
  }

  const isActionRunning = activeAction !== null;

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

      {!isLoading && !errorMessage && report ? (
        <section className="parsed-observations-section">
          <div className="subsection-header">
            <div>
              <p className="eyebrow">Review</p>
              <h3>Parsed Observations</h3>
            </div>
            <div className="report-actions">
              <button
                className="action-button"
                type="button"
                onClick={handleExtractText}
                disabled={isActionRunning}
              >
                {activeAction === "extract" ? "Extracting..." : "Extract text"}
              </button>
              <button
                className="action-button"
                type="button"
                onClick={handleParseObservations}
                disabled={isActionRunning}
              >
                {activeAction === "parse" ? "Parsing..." : "Parse observations"}
              </button>
              <button
                className="action-button secondary"
                type="button"
                onClick={handleRefreshParsedObservations}
                disabled={isActionRunning}
              >
                {activeAction === "refresh"
                  ? "Refreshing..."
                  : "Refresh parsed observations"}
              </button>
            </div>
          </div>

          {actionMessage ? (
            <p className="status-message success-message" role="status">
              {actionMessage}
            </p>
          ) : null}

          {parsedErrorMessage ? (
            <p className="status-message error-message" role="alert">
              {parsedErrorMessage}
            </p>
          ) : null}

          {isParsedLoading ? (
            <p className="status-message">Loading parsed observations...</p>
          ) : null}

          {!isParsedLoading && parsedObservations.length === 0 ? (
            <p className="status-message">
              No parsed observations exist for this report yet.
            </p>
          ) : null}

          {!isParsedLoading && parsedObservations.length > 0 ? (
            <div className="table-scroll">
              <table className="reports-table parsed-observations-table">
                <thead>
                  <tr>
                    <th>rawTestName</th>
                    <th>matchedTestId</th>
                    <th>observedAt</th>
                    <th>rawValue</th>
                    <th>numericValue</th>
                    <th>unit</th>
                    <th>referenceRange</th>
                    <th>status</th>
                  </tr>
                </thead>
                <tbody>
                  {parsedObservations.map((observation, index) => (
                    <tr
                      key={observation.id ?? `${observation.rawTestName}-${index}`}
                    >
                      <td>{formatOptionalValue(observation.rawTestName)}</td>
                      <td>{formatOptionalValue(observation.matchedTestId)}</td>
                      <td>{formatOptionalValue(observation.observedAt)}</td>
                      <td>{formatOptionalValue(observation.rawValue)}</td>
                      <td>{formatOptionalValue(observation.numericValue)}</td>
                      <td>{formatOptionalValue(observation.unit)}</td>
                      <td>{formatOptionalValue(observation.referenceRange)}</td>
                      <td>{formatOptionalValue(observation.status)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </section>
      ) : null}
    </section>
  );
}

export default ReportDetailPage;

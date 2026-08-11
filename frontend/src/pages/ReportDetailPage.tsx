import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import {
  confirmParsedObservation,
  deleteReport,
  formatLoadErrorMessage,
  getParsedObservations,
  getReport,
  getTests,
  ParsedObservationResponse,
  rejectParsedObservation,
  ReportResponse,
  TestCatalogResponse,
  updateParsedObservation,
  UpdateParsedObservationRequest
} from "../api/client";
import type { DevUser } from "../api/client";
import StatusBadge from "../components/StatusBadge";

type ParsedObservationEditForm = {
  rawTestName: string;
  matchedTestId: string;
  observedAt: string;
  rawValue: string;
  numericValue: string;
  unit: string;
  referenceRange: string;
};

type ReportDetailLocationState = {
  successMessage?: string;
};

type ReportDetailPageProps = {
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

function formatOptionalValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === "") {
    return "Not provided";
  }

  return value;
}

function canConfirmParsedObservation(observation: ParsedObservationResponse) {
  return (
    observation.status === "NEEDS_REVIEW" &&
    Boolean(observation.id) &&
    Boolean(observation.matchedTestId) &&
    observation.numericValue !== null &&
    observation.numericValue !== undefined
  );
}

function formatShortId(value: string) {
  return value.length > 8 ? `${value.slice(0, 8)}...` : value;
}

function findMatchedTest(
  tests: TestCatalogResponse[],
  matchedTestId: string | null
) {
  if (!matchedTestId) {
    return undefined;
  }

  return tests.find((test) => test.id === matchedTestId);
}

function toEditForm(
  observation: ParsedObservationResponse
): ParsedObservationEditForm {
  return {
    rawTestName: observation.rawTestName ?? "",
    matchedTestId: observation.matchedTestId ?? "",
    observedAt: observation.observedAt ?? "",
    rawValue: observation.rawValue ?? "",
    numericValue:
      observation.numericValue === null || observation.numericValue === undefined
        ? ""
        : String(observation.numericValue),
    unit: observation.unit ?? "",
    referenceRange: observation.referenceRange ?? ""
  };
}

function blankToNull(value: string) {
  const trimmedValue = value.trim();
  return trimmedValue === "" ? null : trimmedValue;
}

function buildUpdatePayload(
  form: ParsedObservationEditForm
): UpdateParsedObservationRequest | null {
  const numericValue =
    form.numericValue.trim() === "" ? null : Number(form.numericValue);

  if (numericValue !== null && Number.isNaN(numericValue)) {
    return null;
  }

  return {
    rawTestName: blankToNull(form.rawTestName),
    matchedTestId: blankToNull(form.matchedTestId),
    observedAt: blankToNull(form.observedAt),
    rawValue: blankToNull(form.rawValue),
    numericValue,
    unit: blankToNull(form.unit),
    referenceRange: blankToNull(form.referenceRange)
  };
}

function ReportDetailPage({ devUser }: ReportDetailPageProps) {
  const { reportId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [report, setReport] = useState<ReportResponse | null>(null);
  const [parsedObservations, setParsedObservations] = useState<
    ParsedObservationResponse[]
  >([]);
  const [tests, setTests] = useState<TestCatalogResponse[]>([]);
  const [isTestsLoading, setIsTestsLoading] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [isParsedLoading, setIsParsedLoading] = useState(false);
  const [activeAction, setActiveAction] = useState<"refresh" | null>(null);
  const [confirmingObservationId, setConfirmingObservationId] = useState<
    string | null
  >(null);
  const [rejectingObservationId, setRejectingObservationId] = useState<
    string | null
  >(null);
  const [editingObservationId, setEditingObservationId] = useState<string | null>(
    null
  );
  const [savingObservationId, setSavingObservationId] = useState<string | null>(
    null
  );
  const [isDeletingReport, setIsDeletingReport] = useState(false);
  const [editForm, setEditForm] = useState<ParsedObservationEditForm>({
    rawTestName: "",
    matchedTestId: "",
    observedAt: "",
    rawValue: "",
    numericValue: "",
    unit: "",
    referenceRange: ""
  });
  const [errorMessage, setErrorMessage] = useState("");
  const [deleteErrorMessage, setDeleteErrorMessage] = useState("");
  const [parsedErrorMessage, setParsedErrorMessage] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const uploadSuccessMessage = (location.state as ReportDetailLocationState | null)
    ?.successMessage;
  const isClinician = devUser.role === "CLINICIAN";

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
    setDeleteErrorMessage("");
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
          formatLoadErrorMessage(error, "Unable to load the report.")
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

  useEffect(() => {
    let isCurrent = true;

    setIsTestsLoading(true);

    getTests()
      .then((testCatalog) => {
        if (isCurrent) {
          setTests(testCatalog);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setParsedErrorMessage(
          formatLoadErrorMessage(error, "Unable to load the test catalog.")
        );
      })
      .finally(() => {
        if (isCurrent) {
          setIsTestsLoading(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, []);

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

  function handleEditParsedObservation(observation: ParsedObservationResponse) {
    if (!observation.id || observation.status === "CONFIRMED") {
      return;
    }

    setParsedErrorMessage("");
    setActionMessage("");
    setEditingObservationId(observation.id);
    setEditForm(toEditForm(observation));
  }

  function handleCancelEdit() {
    setEditingObservationId(null);
    setParsedErrorMessage("");
  }

  function updateEditField(
    field: keyof ParsedObservationEditForm,
    value: string
  ) {
    setEditForm((currentForm) => ({
      ...currentForm,
      [field]: value
    }));
  }

  async function handleSaveParsedObservation(parsedObservationId: string) {
    const payload = buildUpdatePayload(editForm);

    setParsedErrorMessage("");
    setActionMessage("");

    if (!payload) {
      setParsedErrorMessage("Numeric value must be a valid number.");
      return;
    }

    setSavingObservationId(parsedObservationId);

    try {
      await updateParsedObservation(parsedObservationId, payload);
      await refreshReportAndParsedObservations();
      setEditingObservationId(null);
      setActionMessage("Parsed observation updated.");
    } catch (error: unknown) {
      setParsedErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to update parsed observation."
      );
    } finally {
      setSavingObservationId(null);
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

  async function handleConfirmParsedObservation(parsedObservationId: string) {
    setConfirmingObservationId(parsedObservationId);
    setParsedErrorMessage("");
    setActionMessage("");

    try {
      await confirmParsedObservation(parsedObservationId);
      await refreshReportAndParsedObservations();
      setActionMessage("Parsed observation confirmed.");
    } catch (error: unknown) {
      setParsedErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to confirm parsed observation."
      );
    } finally {
      setConfirmingObservationId(null);
    }
  }

  async function handleRejectParsedObservation(parsedObservationId: string) {
    setRejectingObservationId(parsedObservationId);
    setParsedErrorMessage("");
    setActionMessage("");

    try {
      await rejectParsedObservation(parsedObservationId);
      await refreshReportAndParsedObservations();
      setActionMessage("Parsed observation rejected.");
    } catch (error: unknown) {
      setParsedErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to reject parsed observation."
      );
    } finally {
      setRejectingObservationId(null);
    }
  }

  async function handleDeleteReport() {
    if (!reportId) {
      return;
    }

    const confirmed = window.confirm(
      "Delete this report? This cannot be undone."
    );

    if (!confirmed) {
      return;
    }

    setIsDeletingReport(true);
    setDeleteErrorMessage("");
    setParsedErrorMessage("");
    setActionMessage("");

    try {
      await deleteReport(reportId);
      navigate("/reports", {
        state: { successMessage: "Report deleted." }
      });
    } catch (error: unknown) {
      setDeleteErrorMessage(
        error instanceof Error ? error.message : "Unable to delete report."
      );
    } finally {
      setIsDeletingReport(false);
    }
  }

  const isActionRunning =
    activeAction !== null ||
    confirmingObservationId !== null ||
    rejectingObservationId !== null ||
    savingObservationId !== null ||
    isDeletingReport;

  return (
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Reports</p>
          <h2 className="page-title">
            {isClinician ? "Patient report detail" : "Report Detail"}
          </h2>
          <p className="page-description">
            {isClinician
              ? "Review report metadata and extracted diagnostic observations."
              : "Review report metadata and confirm extracted diagnostic observations."}
          </p>
        </div>
        <div className="report-detail-actions">
          <Link className="button-link secondary" to="/reports">
            All Reports
          </Link>
          {!isClinician && !isLoading && !errorMessage && report ? (
            <button
              className="action-button secondary danger"
              type="button"
              onClick={handleDeleteReport}
              disabled={isActionRunning}
            >
              {isDeletingReport ? "Deleting..." : "Delete"}
            </button>
          ) : null}
        </div>
      </div>

      {isLoading ? <p className="status-message">Loading report...</p> : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && deleteErrorMessage ? (
        <p className="status-message error-message" role="alert">
          {deleteErrorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && report ? (
        <dl className="detail-list">
          <div>
            <dt>Original filename</dt>
            <dd className="detail-filename">{report.originalFilename}</dd>
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
            <dd>
              <StatusBadge status={report.status} />
            </dd>
          </div>
          <div>
            <dt>Created</dt>
            <dd>{formatDateTime(report.createdAt)}</dd>
          </div>
        </dl>
      ) : null}

      {!isLoading && !errorMessage && report ? (
        <section className="parsed-observations-section">
          {uploadSuccessMessage ? (
            <p className="status-message success-message" role="status">
              {uploadSuccessMessage}
            </p>
          ) : null}

          <div className="parsed-observations-header">
            <div>
              <h3>Parsed Observations</h3>
              <p className="parse-observations-helper">
                {isClinician
                  ? "Review extracted observations for the selected assigned patient."
                  : "Review extracted observations before confirming them into trends."}
              </p>
            </div>
            <div className="parsed-observations-actions">
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
              No observations were detected in this report.
            </p>
          ) : null}

          {!isParsedLoading && parsedObservations.length > 0 ? (
            <div className="table-scroll">
              <table className="reports-table parsed-observations-table">
                <colgroup>
                  <col className="raw-test-column" />
                  <col className="matched-test-column" />
                  <col className="observed-at-column" />
                  <col className="raw-value-column" />
                  <col className="numeric-value-column" />
                  <col className="unit-column" />
                  <col className="status-column" />
                  <col className="actions-column" />
                </colgroup>
                <thead>
                  <tr>
                    <th>Raw test</th>
                    <th>Matched test</th>
                    <th className="nowrap-cell">Observed</th>
                    <th>Raw value</th>
                    <th>Numeric value</th>
                    <th>Unit</th>
                    <th>Status</th>
                    <th className="actions-column">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {parsedObservations.map((observation, index) => {
                    const rowKey =
                      observation.id ?? `${observation.rawTestName}-${index}`;
                    const matchedTest = findMatchedTest(
                      tests,
                      observation.matchedTestId
                    );
                    const isConfirmable =
                      canConfirmParsedObservation(observation);
                    const isConfirming =
                      observation.id === confirmingObservationId;
                    const isRejecting =
                      observation.id === rejectingObservationId;
                    const isConfirmed = observation.status === "CONFIRMED";
                    const isReviewable = observation.status === "NEEDS_REVIEW";
                    const isEditing = observation.id === editingObservationId;
                    const canEdit =
                      !isClinician && Boolean(observation.id) && isReviewable;
                    const canReject = !isClinician && Boolean(observation.id) && isReviewable;
                    const isSaving = observation.id === savingObservationId;
                    const selectedMatchedTestExists =
                      editForm.matchedTestId === "" ||
                      tests.some((test) => test.id === editForm.matchedTestId);

                    return (
                      <tr
                        className={
                          isConfirmed
                            ? "confirmed-observation-row"
                            : undefined
                        }
                        key={rowKey}
                      >
                        <td>
                          <span className="raw-test-cell">
                            {isEditing ? (
                              <input
                                className="table-input"
                                type="text"
                                value={editForm.rawTestName}
                                onChange={(event) =>
                                  updateEditField(
                                    "rawTestName",
                                    event.target.value
                                  )
                                }
                              />
                            ) : (
                              formatOptionalValue(observation.rawTestName)
                            )}
                          </span>
                        </td>
                        <td>
                          {isEditing ? (
                            <select
                              className="table-input"
                              value={editForm.matchedTestId}
                              onChange={(event) =>
                                updateEditField(
                                  "matchedTestId",
                                  event.target.value
                                )
                              }
                              disabled={isTestsLoading}
                            >
                              <option value="">No matched test</option>
                              {!selectedMatchedTestExists ? (
                                <option value={editForm.matchedTestId}>
                                  Current: {editForm.matchedTestId}
                                </option>
                              ) : null}
                              {tests.map((test) => (
                                <option key={test.id} value={test.id}>
                                  {test.displayName}
                                </option>
                              ))}
                            </select>
                          ) : observation.matchedTestId ? (
                            <span
                              className="matched-test-cell"
                              title={observation.matchedTestId}
                            >
                              <span className="matched-test-name">
                                {matchedTest?.displayName ??
                                  "Catalog match unavailable"}
                              </span>
                              <span className="muted-id">
                                ID {formatShortId(observation.matchedTestId)}
                              </span>
                            </span>
                          ) : (
                            "Not provided"
                          )}
                        </td>
                        <td className="nowrap-cell">
                          {isEditing ? (
                            <input
                              className="table-input date-input"
                              type="date"
                              value={editForm.observedAt}
                              onChange={(event) =>
                                updateEditField("observedAt", event.target.value)
                              }
                            />
                          ) : (
                            formatOptionalValue(observation.observedAt)
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <input
                              className="table-input"
                              type="text"
                              value={editForm.rawValue}
                              onChange={(event) =>
                                updateEditField("rawValue", event.target.value)
                              }
                            />
                          ) : (
                            formatOptionalValue(observation.rawValue)
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <input
                              className="table-input"
                              type="number"
                              step="any"
                              value={editForm.numericValue}
                              onChange={(event) =>
                                updateEditField(
                                  "numericValue",
                                  event.target.value
                                )
                              }
                            />
                          ) : (
                            formatOptionalValue(observation.numericValue)
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <input
                              className="table-input"
                              type="text"
                              value={editForm.unit}
                              onChange={(event) =>
                                updateEditField("unit", event.target.value)
                              }
                            />
                          ) : (
                            formatOptionalValue(observation.unit)
                          )}
                        </td>
                        <td>
                          <StatusBadge status={observation.status} />
                        </td>
                        <td className="actions-column">
                          {isEditing && observation.id ? (
                            <div className="table-action-group">
                              <button
                                className="action-button table-action-button"
                                type="button"
                                onClick={() =>
                                  handleSaveParsedObservation(observation.id!)
                                }
                                disabled={isActionRunning}
                              >
                                {isSaving ? "Saving..." : "Save"}
                              </button>
                              <button
                                className="action-button secondary table-action-button"
                                type="button"
                                onClick={handleCancelEdit}
                                disabled={isActionRunning}
                              >
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <div className="table-action-group">
                              {canEdit ? (
                                <button
                                  className="action-button secondary table-action-button"
                                  type="button"
                                  onClick={() =>
                                    handleEditParsedObservation(observation)
                                  }
                                  disabled={
                                    isActionRunning ||
                                    editingObservationId !== null
                                  }
                                >
                                  Edit
                                </button>
                              ) : null}
                              {isClinician ? (
                                <span className="dashboard-summary-detail">
                                  View only
                                </span>
                              ) : null}
                              {!isClinician && isConfirmable && observation.id ? (
                                <button
                                  className="action-button table-action-button"
                                  type="button"
                                  onClick={() =>
                                    handleConfirmParsedObservation(observation.id!)
                                  }
                                  disabled={isActionRunning}
                                >
                                  {isConfirming ? "Confirming..." : "Confirm"}
                                </button>
                              ) : null}
                              {canReject && observation.id ? (
                                <button
                                  className="action-button secondary danger table-action-button"
                                  type="button"
                                  onClick={() =>
                                    handleRejectParsedObservation(observation.id!)
                                  }
                                  disabled={isActionRunning}
                                >
                                  {isRejecting ? "Rejecting..." : "Reject"}
                                </button>
                              ) : null}
                              {!isClinician && !isReviewable ? (
                                <span className="dashboard-summary-detail">
                                  No action
                                </span>
                              ) : null}
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
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

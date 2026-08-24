import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  createObservation,
  formatLoadErrorMessage,
  getCurrentDevUser,
  getTests,
  LabObservationResponse,
  TestCatalogResponse
} from "../api/client";
import { getPatientVaultService } from "../vault";
import { getPatientVaultMode } from "../vault/config";
import type { VaultObservation } from "../vault";

type ObservationFormState = {
  testId: string;
  testName: string;
  observedAt: string;
  numericValue: string;
  unit: string;
  referenceLow: string;
  referenceHigh: string;
  abnormalFlag: string;
};

const initialFormState: ObservationFormState = {
  testId: "",
  testName: "",
  observedAt: "",
  numericValue: "",
  unit: "",
  referenceLow: "",
  referenceHigh: "",
  abnormalFlag: "NORMAL"
};

function AddObservationPage() {
  const [tests, setTests] = useState<TestCatalogResponse[]>([]);
  const [form, setForm] = useState<ObservationFormState>(initialFormState);
  const [isLoadingTests, setIsLoadingTests] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [successObservation, setSuccessObservation] =
    useState<LabObservationResponse | VaultObservation | null>(null);
  const isLocalVaultMode = getPatientVaultMode() === "local";

  const selectedTest = useMemo(
    () => tests.find((test) => test.id === form.testId),
    [form.testId, tests]
  );

  useEffect(() => {
    let isCurrent = true;

    if (isLocalVaultMode) {
      setIsLoadingTests(false);
      return () => {
        isCurrent = false;
      };
    }

    getTests()
      .then((testCatalog) => {
        if (!isCurrent) {
          return;
        }

        setTests(testCatalog);

        if (testCatalog.length > 0) {
          const firstTest = testCatalog[0];
          setForm((currentForm) => ({
            ...currentForm,
            testId: firstTest.id,
            unit: firstTest.defaultUnit ?? ""
          }));
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load the test catalog.")
        );
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoadingTests(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [isLocalVaultMode]);

  function updateField(field: keyof ObservationFormState, value: string) {
    setForm((currentForm) => ({
      ...currentForm,
      [field]: value
    }));
  }

  function updateSelectedTest(testId: string) {
    const nextTest = tests.find((test) => test.id === testId);

    setForm((currentForm) => ({
      ...currentForm,
      testId,
      unit: nextTest?.defaultUnit ?? ""
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");
    setSuccessObservation(null);

    const numericValue = Number(form.numericValue);
    const referenceLow = Number(form.referenceLow);
    const referenceHigh = Number(form.referenceHigh);
    const testName = form.testName.trim();
    const unit = form.unit.trim();

    if (isLocalVaultMode) {
      if (!testName || !form.observedAt || !unit || Number.isNaN(numericValue)) {
        setErrorMessage("Complete test name, observed date, value, and unit.");
        return;
      }

      if (
        (form.referenceLow.trim() && Number.isNaN(referenceLow)) ||
        (form.referenceHigh.trim() && Number.isNaN(referenceHigh))
      ) {
        setErrorMessage("Reference range values must be valid numbers.");
        return;
      }
    } else {
      if (
        !form.testId ||
        !form.observedAt ||
        !unit ||
        Number.isNaN(numericValue) ||
        Number.isNaN(referenceLow) ||
        Number.isNaN(referenceHigh)
      ) {
        setErrorMessage("Complete all fields with valid numeric values.");
        return;
      }
    }

    setIsSubmitting(true);

    try {
      const createdObservation = isLocalVaultMode
        ? await saveLocalObservation({
            abnormalFlag: form.abnormalFlag,
            numericValue,
            observedAt: form.observedAt,
            referenceHigh: form.referenceHigh.trim() ? referenceHigh : null,
            referenceLow: form.referenceLow.trim() ? referenceLow : null,
            testName,
            unit
          })
        : await createObservation({
            testId: form.testId,
            observedAt: form.observedAt,
            numericValue,
            unit,
            referenceLow,
            referenceHigh,
            abnormalFlag: form.abnormalFlag
          });

      setSuccessObservation(createdObservation);
      setForm((currentForm) => ({
        ...initialFormState,
        testId: currentForm.testId,
        testName: isLocalVaultMode ? "" : currentForm.testName,
        observedAt: currentForm.observedAt,
        unit: selectedTest?.defaultUnit ?? currentForm.unit
      }));
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to create the observation."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function saveLocalObservation({
    abnormalFlag,
    numericValue,
    observedAt,
    referenceHigh,
    referenceLow,
    testName,
    unit
  }: {
    abnormalFlag: string;
    numericValue: number;
    observedAt: string;
    referenceHigh: number | null;
    referenceLow: number | null;
    testName: string;
    unit: string;
  }) {
    const currentUser = getCurrentDevUser();
    const timestamp = new Date().toISOString();
    const observationId = createLocalId("observation");
    const testId = createManualTestId(testName);
    const observation: VaultObservation = {
      resourceType: "Observation",
      observationId,
      patientUserId: currentUser.userId,
      testId,
      testName,
      observedAt,
      valueText: String(numericValue),
      numericValue,
      unit,
      referenceRange: formatReferenceRange(referenceLow, referenceHigh),
      abnormalFlag,
      status: "CONFIRMED",
      sourceType: "MANUAL",
      reportId: null,
      reportOriginalFilename: null,
      labName: null,
      reportDate: null,
      parsedObservationId: null,
      createdAt: timestamp,
      updatedAt: timestamp
    };

    const savedObservation =
      await getPatientVaultService().saveConfirmedObservation(observation);

    await getPatientVaultService().recordAuditEvent({
      resourceType: "AuditEvent",
      auditEventId: "",
      actorUserId: currentUser.userId,
      actorRole: currentUser.role,
      patientUserId: currentUser.userId,
      action: "MANUAL_OBSERVATION_ADDED",
      resourceTypeName: "OBSERVATION",
      resourceId: savedObservation.observationId,
      details: null,
      createdAt: new Date().toISOString()
    });

    return savedObservation;
  }

  return (
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Observations</p>
          <h2 className="page-title">Add Manual Observation</h2>
          <p className="page-description">
            {isLocalVaultMode
              ? "Save a confirmed result directly in your local encrypted vault."
              : "Manually enter lab values when a report is not available or extraction needs correction."}
          </p>
        </div>
      </div>
      <div className="form-card observation-form-card">
        {isLocalVaultMode ? (
          <p className="status-message local-prototype-message">
            This observation will be saved in your local encrypted vault and
            will appear in Trends after saving.
          </p>
        ) : null}

        <form className="observation-form manual-observation-form" onSubmit={handleSubmit}>
          {isLocalVaultMode ? (
            <label className="full-span-field">
              Test name
              <input
                type="text"
                value={form.testName}
                onChange={(event) => updateField("testName", event.target.value)}
                placeholder="Hemoglobin"
                required
              />
            </label>
          ) : (
            <label className="full-span-field">
              Test
              <select
                value={form.testId}
                onChange={(event) => updateSelectedTest(event.target.value)}
                disabled={isLoadingTests || tests.length === 0}
                required
              >
                {isLoadingTests ? (
                  <option value="">Loading tests...</option>
                ) : (
                  tests.map((test) => (
                    <option key={test.id} value={test.id}>
                      {test.displayName}
                    </option>
                  ))
                )}
              </select>
            </label>
          )}

          <label>
            Observed date
            <input
              type="date"
              value={form.observedAt}
              onChange={(event) => updateField("observedAt", event.target.value)}
              required
            />
          </label>

          <label>
            Value
            <input
              type="number"
              step="any"
              value={form.numericValue}
              onChange={(event) =>
                updateField("numericValue", event.target.value)
              }
              required
            />
          </label>

          <label>
            Unit
            <span className="field-helper">
              {isLocalVaultMode
                ? "Enter the unit shown with the result"
                : "Auto-filled from selected test"}
            </span>
            <input
              type="text"
              value={form.unit}
              onChange={(event) => updateField("unit", event.target.value)}
              required
            />
          </label>

          <fieldset className="reference-range-group">
            <legend>Reference range</legend>
            <label>
              Low{" "}
              {isLocalVaultMode ? (
                <span className="optional-label">Optional</span>
              ) : null}
              <input
                type="number"
                step="any"
                value={form.referenceLow}
                onChange={(event) =>
                  updateField("referenceLow", event.target.value)
                }
                required={!isLocalVaultMode}
              />
            </label>

            <label>
              High{" "}
              {isLocalVaultMode ? (
                <span className="optional-label">Optional</span>
              ) : null}
              <input
                type="number"
                step="any"
                value={form.referenceHigh}
                onChange={(event) =>
                  updateField("referenceHigh", event.target.value)
                }
                required={!isLocalVaultMode}
              />
            </label>
          </fieldset>

          <label>
            Abnormal flag
            <select
              value={form.abnormalFlag}
              onChange={(event) => updateField("abnormalFlag", event.target.value)}
              required
            >
              <option value="NORMAL">Normal</option>
              <option value="LOW">Low</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </label>

          <button type="submit" disabled={isSubmitting || isLoadingTests}>
            {isSubmitting ? "Saving..." : "Save observation"}
          </button>
        </form>
      </div>

      {successObservation ? (
        <div className="status-message success-message" role="status">
          <p>
            {isLocalVaultMode
              ? "Observation saved to your local encrypted vault."
              : `Created ${successObservation.testName}: ${successObservation.numericValue} ${successObservation.unit}`}
          </p>
          {isLocalVaultMode ? (
            <div className="message-actions">
              <Link className="button-link secondary" to="/trends">
                View Trends
              </Link>
              <button
                className="action-button secondary"
                type="button"
                onClick={() => setSuccessObservation(null)}
              >
                Add another observation
              </button>
            </div>
          ) : null}
        </div>
      ) : null}

      {errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

function createLocalId(prefix: string) {
  const randomId =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

  return `${prefix}_${randomId}`;
}

function createManualTestId(testName: string) {
  const normalizedName = testName
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

  return `manual:${normalizedName || createLocalId("test")}`;
}

function formatReferenceRange(
  referenceLow: number | null,
  referenceHigh: number | null
) {
  if (referenceLow !== null && referenceHigh !== null) {
    return `${referenceLow} - ${referenceHigh}`;
  }

  if (referenceLow !== null) {
    return `>= ${referenceLow}`;
  }

  if (referenceHigh !== null) {
    return `<= ${referenceHigh}`;
  }

  return null;
}

export default AddObservationPage;

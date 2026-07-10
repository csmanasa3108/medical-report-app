import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createObservation,
  getTests,
  LabObservationResponse,
  TestCatalogResponse
} from "../api/client";

type ObservationFormState = {
  testId: string;
  observedAt: string;
  numericValue: string;
  unit: string;
  referenceLow: string;
  referenceHigh: string;
  abnormalFlag: string;
};

const initialFormState: ObservationFormState = {
  testId: "",
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
    useState<LabObservationResponse | null>(null);

  const selectedTest = useMemo(
    () => tests.find((test) => test.id === form.testId),
    [form.testId, tests]
  );

  useEffect(() => {
    let isCurrent = true;

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
          error instanceof Error
            ? error.message
            : "Unable to load the test catalog."
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
  }, []);

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

    if (
      !form.testId ||
      !form.observedAt ||
      !form.unit.trim() ||
      Number.isNaN(numericValue) ||
      Number.isNaN(referenceLow) ||
      Number.isNaN(referenceHigh)
    ) {
      setErrorMessage("Complete all fields with valid numeric values.");
      return;
    }

    setIsSubmitting(true);

    try {
      const createdObservation = await createObservation({
        testId: form.testId,
        observedAt: form.observedAt,
        numericValue,
        unit: form.unit.trim(),
        referenceLow,
        referenceHigh,
        abnormalFlag: form.abnormalFlag
      });

      setSuccessObservation(createdObservation);
      setForm((currentForm) => ({
        ...initialFormState,
        testId: currentForm.testId,
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

  return (
    <section className="page-section">
      <h2>Add Observation</h2>
      <form className="observation-form" onSubmit={handleSubmit}>
        <label>
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
            onChange={(event) => updateField("numericValue", event.target.value)}
            required
          />
        </label>

        <label>
          Unit
          <input
            type="text"
            value={form.unit}
            onChange={(event) => updateField("unit", event.target.value)}
            required
          />
        </label>

        <label>
          Reference low
          <input
            type="number"
            step="any"
            value={form.referenceLow}
            onChange={(event) => updateField("referenceLow", event.target.value)}
            required
          />
        </label>

        <label>
          Reference high
          <input
            type="number"
            step="any"
            value={form.referenceHigh}
            onChange={(event) => updateField("referenceHigh", event.target.value)}
            required
          />
        </label>

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
          {isSubmitting ? "Saving..." : "Save Observation"}
        </button>
      </form>

      {successObservation ? (
        <p className="status-message success-message" role="status">
          Created {successObservation.testName}: {successObservation.numericValue}{" "}
          {successObservation.unit}
        </p>
      ) : null}

      {errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

export default AddObservationPage;

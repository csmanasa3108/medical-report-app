import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  formatLoadErrorMessage,
  getAssignedPatients,
  getLabTrend,
  getSelectedAssignedPatientId,
  getTests
} from "../api/client";
import type {
  AssignedPatientResponse,
  DevUser,
  TestCatalogResponse
} from "../api/client";

type TrendsPageProps = {
  devUser: DevUser;
};

type TrendAvailability = {
  test: TestCatalogResponse;
  pointCount: number;
  unit: string;
};

function getTestName(test: TestCatalogResponse) {
  return test.displayName || test.canonicalName;
}

function hasUsableTrendPoint(point: {
  observedAt?: string | null;
  date?: string;
  numericValue?: number | string | null;
  value?: number | string;
}) {
  const observedAt = point.observedAt ?? point.date;
  const rawValue = point.numericValue ?? point.value;
  return Boolean(observedAt) && Number.isFinite(Number(rawValue));
}

function TrendsEmptyState({ isClinician }: { isClinician: boolean }) {
  return (
    <section className="trends-empty-state">
      <div>
        <p className="eyebrow">Confirmed results</p>
        <h3>No confirmed trends yet</h3>
        <p>
          Confirm extracted results from the Review Queue to start building
          trends.
        </p>
      </div>
      <div className="trends-empty-actions">
        <Link className="button-link" to="/review">
          Open Review Queue
        </Link>
        {isClinician ? (
          <Link className="button-link secondary" to="/reports">
            View Patient Reports
          </Link>
        ) : (
          <Link className="button-link secondary" to="/upload">
            Upload Report
          </Link>
        )}
      </div>
    </section>
  );
}

function SelectPatientState() {
  return (
    <section className="trends-empty-state">
      <div>
        <p className="eyebrow">Patient required</p>
        <h3>Select a patient to view trends.</h3>
        <p>Choose an assigned patient before opening confirmed trend views.</p>
      </div>
      <Link className="button-link" to="/patients">
        Select Patient
      </Link>
    </section>
  );
}

function TrendsPage({ devUser }: TrendsPageProps) {
  const [trends, setTrends] = useState<TrendAvailability[]>([]);
  const [assignedPatients, setAssignedPatients] = useState<
    AssignedPatientResponse[]
  >([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingPatientContext, setIsLoadingPatientContext] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [patientContextErrorMessage, setPatientContextErrorMessage] =
    useState("");
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
    setTrends([]);

    if (isClinician && !selectedPatientId) {
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

    getTests()
      .then(async (testCatalog) => {
        const trendResults = await Promise.allSettled(
          testCatalog.map((test) => getLabTrend(test.id, selectedPatientId))
        );

        if (!isCurrent) {
          return;
        }

        const availableTrends = testCatalog.flatMap((test, index) => {
          const trendResult = trendResults[index];

          if (trendResult.status !== "fulfilled") {
            return [];
          }

          const points = trendResult.value.points.filter(hasUsableTrendPoint);

          if (points.length === 0) {
            return [];
          }

          return [
            {
              test,
              pointCount: points.length,
              unit: trendResult.value.unit ?? test.defaultUnit ?? ""
            }
          ];
        });

        setTrends(availableTrends);
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load trends.")
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

  return (
    <section className="trends-page">
      <div className="trends-page-header">
        <div>
          <p className="eyebrow">Confirmed results</p>
          <h2 className="page-title">Trends</h2>
          <p className="page-description">
            Track confirmed results over time.
          </p>
        </div>
        <div className="trends-header-actions">
          <Link className="button-link secondary" to="/review">
            Open Review Queue
          </Link>
          <Link className="button-link secondary" to="/reports">
            Reports
          </Link>
        </div>
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
        <p className="status-message error-message trends-state-message" role="alert">
          {patientContextErrorMessage}
        </p>
      ) : null}

      {isLoading ? (
        <p className="status-message trends-state-message">Loading trends...</p>
      ) : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message trends-state-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && isClinician && !selectedPatientId ? (
        <SelectPatientState />
      ) : null}

      {!isLoading &&
      !errorMessage &&
      (!isClinician || selectedPatientId) &&
      trends.length === 0 ? (
        <TrendsEmptyState isClinician={isClinician} />
      ) : null}

      {!isLoading && !errorMessage && trends.length > 0 ? (
        <section className="trend-selector-panel">
          <div className="trend-selector-header">
            <div>
              <p className="eyebrow">Test selector</p>
              <h3>Available confirmed trends</h3>
            </div>
            <span className="review-count-pill">
              {trends.length.toLocaleString()}{" "}
              {trends.length === 1 ? "test" : "tests"}
            </span>
          </div>
          <div className="trends-grid" aria-label="Available lab test trends">
            {trends.map(({ pointCount, test, unit }) => (
              <article className="trend-test-card" key={test.id}>
                <div>
                  {test.category ? (
                    <p className="trend-test-category">{test.category}</p>
                  ) : null}
                  <h3>{getTestName(test)}</h3>
                  <dl className="trend-test-meta">
                    <div>
                      <dt>Confirmed points</dt>
                      <dd>{pointCount.toLocaleString()}</dd>
                    </div>
                    <div>
                      <dt>Unit</dt>
                      <dd>{unit || "Not provided"}</dd>
                    </div>
                  </dl>
                </div>
                <Link
                  className="button-link secondary"
                  to={`/tests/${test.id}/trend`}
                >
                  View trend
                </Link>
              </article>
            ))}
          </div>
        </section>
      ) : null}
    </section>
  );
}

export default TrendsPage;

import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  formatLoadErrorMessage,
  getSelectedAssignedPatientId,
  getTests,
  TestCatalogResponse
} from "../api/client";
import type { DevUser } from "../api/client";

type TrendsPageProps = {
  devUser: DevUser;
};

function getTestName(test: TestCatalogResponse) {
  return test.displayName || test.canonicalName;
}

function TrendsPage({ devUser }: TrendsPageProps) {
  const [tests, setTests] = useState<TestCatalogResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const isClinician = devUser.role === "CLINICIAN";
  const selectedPatientId = isClinician ? getSelectedAssignedPatientId() : null;

  useEffect(() => {
    let isCurrent = true;

    setIsLoading(true);
    setErrorMessage("");
    setTests([]);

    if (isClinician && !selectedPatientId) {
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

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

        setErrorMessage(
          formatLoadErrorMessage(error, "Unable to load the test catalog.")
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
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Trend analytics</p>
          <h2 className="page-title">Trends</h2>
          <p className="page-description">
            {isClinician
              ? "Track diagnostic values over time for the selected assigned patient."
              : "Track diagnostic values over time by lab test."}
          </p>
        </div>
      </div>

      {isLoading ? (
        <p className="status-message trends-state-message">Loading tests...</p>
      ) : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message trends-state-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && isClinician && !selectedPatientId ? (
        <p className="status-message trends-state-message">
          Select an assigned patient first.
        </p>
      ) : null}

      {!isLoading &&
      !errorMessage &&
      (!isClinician || selectedPatientId) &&
      tests.length === 0 ? (
        <div className="empty-state">
          No lab tests are available for trend viewing.
        </div>
      ) : null}

      {!isLoading && !errorMessage && tests.length > 0 ? (
        <div className="trends-grid" aria-label="Available lab test trends">
          {tests.map((test) => (
            <article className="trend-test-card" key={test.id}>
              <div>
                {test.category ? (
                  <p className="trend-test-category">{test.category}</p>
                ) : null}
                <h3>{getTestName(test)}</h3>
                {test.defaultUnit ? (
                  <dl className="trend-test-meta">
                    <div>
                      <dt>Default unit</dt>
                      <dd>{test.defaultUnit}</dd>
                    </div>
                  </dl>
                ) : null}
              </div>
              <Link className="button-link secondary" to={`/tests/${test.id}/trend`}>
                View trend
              </Link>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}

export default TrendsPage;

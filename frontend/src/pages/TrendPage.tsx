import { useParams } from "react-router-dom";

function TrendPage() {
  const { testId } = useParams();
  const displayName = testId ? testId.replaceAll("-", " ") : "selected test";

  return (
    <section className="page-section">
      <h2>Trend: {displayName}</h2>
      <p>
        A simple trend graph for this lab test will appear here once observation
        data is available.
      </p>
    </section>
  );
}

export default TrendPage;

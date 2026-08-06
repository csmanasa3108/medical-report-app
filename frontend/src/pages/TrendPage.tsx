import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  getLabTrend,
  getTests,
  LabTrendResponse,
  TestCatalogResponse
} from "../api/client";

type ChartPoint = {
  date: string;
  value: number;
};

type Summary = {
  latestValue: number | null;
  previousValue: number | null;
  absoluteChange: number | null;
  percentChange: number | null;
};

function getCatalogTestName(test: TestCatalogResponse | undefined) {
  return test?.displayName || test?.canonicalName || test?.name || "";
}

function formatNumber(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "N/A";
  }

  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 2
  }).format(value);
}

function formatPercent(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "N/A";
  }

  return `${formatNumber(value)}%`;
}

function formatValueWithUnit(value: number | null | undefined, unit: string) {
  const formattedValue = formatNumber(value);

  if (formattedValue === "N/A" || !unit) {
    return formattedValue;
  }

  return `${formattedValue} ${unit}`;
}

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

function sortTrendPoints(points: ChartPoint[]) {
  return [...points].sort(
    (left, right) =>
      new Date(left.date).getTime() - new Date(right.date).getTime()
  );
}

function getSummary(trend: LabTrendResponse, points: ChartPoint[]): Summary {
  const latestPoint = points[points.length - 1];
  const previousPoint = points[points.length - 2];
  const latestValue = trend.latestValue ?? latestPoint?.value ?? null;
  const previousValue =
    trend.previousValue ?? previousPoint?.value ?? null;
  const absoluteChange =
    trend.absoluteChange ??
    (latestValue !== null && previousValue !== null
      ? latestValue - previousValue
      : null);
  const percentChange =
    trend.percentChange ??
    (absoluteChange !== null && previousValue
      ? (absoluteChange / previousValue) * 100
      : null);

  return {
    latestValue,
    previousValue,
    absoluteChange,
    percentChange
  };
}

function TrendLineChart({ points, unit }: { points: ChartPoint[]; unit: string }) {
  const width = 720;
  const height = 320;
  const padding = 52;
  const values = points.map((point) => point.value);
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const valueRange = maxValue - minValue || 1;
  const xStep =
    points.length > 1 ? (width - padding * 2) / (points.length - 1) : 0;

  const plottedPoints = points.map((point, index) => {
    const x = points.length === 1 ? width / 2 : padding + index * xStep;
    const y =
      height -
      padding -
      ((point.value - minValue) / valueRange) * (height - padding * 2);

    return { ...point, x, y };
  });

  const path = plottedPoints
    .map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`)
    .join(" ");

  return (
    <div
      className="trend-chart"
      aria-label={`Trend chart${unit ? ` in ${unit}` : ""}`}
    >
      <svg viewBox={`0 0 ${width} ${height}`} role="img">
        <title>Lab trend over time</title>
        <line
          className="chart-grid"
          x1={padding}
          x2={width - padding}
          y1={padding}
          y2={padding}
        />
        <line
          className="chart-grid"
          x1={padding}
          x2={width - padding}
          y1={height / 2}
          y2={height / 2}
        />
        <line
          className="chart-axis"
          x1={padding}
          x2={width - padding}
          y1={height - padding}
          y2={height - padding}
        />
        <line
          className="chart-axis"
          x1={padding}
          x2={padding}
          y1={padding}
          y2={height - padding}
        />
        <text className="chart-label" x={padding} y={padding - 16}>
          {formatValueWithUnit(maxValue, unit)}
        </text>
        <text className="chart-label" x={padding} y={height - padding + 28}>
          {formatValueWithUnit(minValue, unit)}
        </text>
        {points.length > 1 ? <path className="chart-line" d={path} /> : null}
        {plottedPoints.map((point) => (
          <g key={`${point.date}-${point.value}`}>
            <title>
              {formatDate(point.date)}: {formatValueWithUnit(point.value, unit)}
            </title>
            <circle className="chart-point" cx={point.x} cy={point.y} r="6" />
            <text className="chart-point-label" x={point.x} y={point.y - 12}>
              {formatNumber(point.value)}
            </text>
          </g>
        ))}
      </svg>
      <div className="chart-dates">
        {points.map((point) => (
          <span key={point.date}>{formatDate(point.date)}</span>
        ))}
      </div>
    </div>
  );
}

function TrendPage() {
  const { testId } = useParams();
  const [trend, setTrend] = useState<LabTrendResponse | null>(null);
  const [tests, setTests] = useState<TestCatalogResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    let isCurrent = true;

    if (!testId) {
      setTrend(null);
      setErrorMessage("No test was selected.");
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage("");

    Promise.allSettled([getLabTrend(testId), getTests()])
      .then(([trendResult, testsResult]) => {
        if (!isCurrent) {
          return;
        }

        if (trendResult.status === "rejected") {
          throw trendResult.reason;
        }

        setTrend(trendResult.value);

        if (testsResult.status === "fulfilled") {
          setTests(testsResult.value);
        } else {
          setTests([]);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setErrorMessage(
          error instanceof Error ? error.message : "Unable to load trend data."
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
  }, [testId]);

  const points = useMemo(() => {
    const mappedPoints =
      trend?.points.map((point) => ({
        date: point.date,
        value: Number(point.value)
      })) ?? [];

    return sortTrendPoints(
      mappedPoints.filter((point) => Number.isFinite(point.value))
    );
  }, [trend?.points]);
  const summary = useMemo(
    () => (trend ? getSummary(trend, points) : null),
    [points, trend]
  );
  const selectedTest = useMemo(
    () => tests.find((test) => test.id === (trend?.testId ?? testId)),
    [testId, tests, trend?.testId]
  );
  const trendName =
    getCatalogTestName(selectedTest) || trend?.testName?.trim() || "Selected Test";
  const trendUnit = trend?.unit ?? selectedTest?.defaultUnit ?? "";
  const trendTitle = trend ? `${trendName} Trend` : "Trend";

  return (
    <section className="page-section">
      {isLoading ? (
        <p className="status-message trend-state-message">Loading trend data...</p>
      ) : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message trend-state-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && trend ? (
        <>
          <div className="trend-header">
            <div>
              <p className="eyebrow">Trend</p>
              <h2 className="page-title">{trendTitle}</h2>
              <p className="page-description">
                {trendUnit
                  ? `Unit: ${trendUnit}`
                  : "Review diagnostic values over time."}
              </p>
            </div>
            <div className="trend-header-actions">
              {trendUnit ? <span className="unit-badge">{trendUnit}</span> : null}
              <Link className="button-link secondary" to="/trends">
                Back to Trends
              </Link>
            </div>
          </div>

          <dl className="trend-summary">
            <div>
              <dt>Latest value</dt>
              <dd>{formatValueWithUnit(summary?.latestValue, trendUnit)}</dd>
            </div>
            <div>
              <dt>Previous value</dt>
              <dd>{formatValueWithUnit(summary?.previousValue, trendUnit)}</dd>
            </div>
            <div>
              <dt>Absolute change</dt>
              <dd>{formatValueWithUnit(summary?.absoluteChange, trendUnit)}</dd>
            </div>
            <div>
              <dt>Percent change</dt>
              <dd>{formatPercent(summary?.percentChange)}</dd>
            </div>
          </dl>

          {points.length === 0 ? (
            <div className="trend-chart-empty">
              No trend data yet for this test.
            </div>
          ) : (
            <section className="trend-chart-card" aria-label="Trend chart">
              <div className="trend-chart-card-header">
                <div>
                  <h3>Diagnostic trend</h3>
                  <p>
                    {points.length} point{points.length === 1 ? "" : "s"} plotted
                    over time.
                  </p>
                </div>
              </div>
              <TrendLineChart points={points} unit={trendUnit} />
            </section>
          )}
        </>
      ) : null}
    </section>
  );
}

export default TrendPage;

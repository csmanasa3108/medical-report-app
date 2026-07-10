import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { getLabTrend, LabTrendResponse } from "../api/client";

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
  const padding = 44;
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
    <div className="trend-chart" aria-label={`Trend chart in ${unit}`}>
      <svg viewBox={`0 0 ${width} ${height}`} role="img">
        <title>Lab trend over time</title>
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
          {formatNumber(maxValue)} {unit}
        </text>
        <text className="chart-label" x={padding} y={height - padding + 28}>
          {formatNumber(minValue)} {unit}
        </text>
        {points.length > 1 ? <path className="chart-line" d={path} /> : null}
        {plottedPoints.map((point) => (
          <g key={`${point.date}-${point.value}`}>
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

    getLabTrend(testId)
      .then((trendResponse) => {
        if (isCurrent) {
          setTrend(trendResponse);
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

  return (
    <section className="page-section">
      {isLoading ? (
        <p className="status-message">Loading trend data...</p>
      ) : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && trend ? (
        <>
          <div className="trend-header">
            <div>
              <p className="eyebrow">Trend</p>
              <h2>{trend.testName}</h2>
            </div>
            <span className="unit-badge">{trend.unit}</span>
          </div>

          <dl className="trend-summary">
            <div>
              <dt>Latest value</dt>
              <dd>
                {formatNumber(summary?.latestValue)} {trend.unit}
              </dd>
            </div>
            <div>
              <dt>Previous value</dt>
              <dd>
                {formatNumber(summary?.previousValue)} {trend.unit}
              </dd>
            </div>
            <div>
              <dt>Absolute change</dt>
              <dd>
                {formatNumber(summary?.absoluteChange)} {trend.unit}
              </dd>
            </div>
            <div>
              <dt>Percent change</dt>
              <dd>{formatPercent(summary?.percentChange)}</dd>
            </div>
          </dl>

          {points.length === 0 ? (
            <div className="trend-chart-empty">
              No chart points were returned for this test.
            </div>
          ) : (
            <TrendLineChart points={points} unit={trend.unit} />
          )}
        </>
      ) : null}
    </section>
  );
}

export default TrendPage;

import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  formatLoadErrorMessage,
  getLabTrend,
  getSelectedAssignedPatientId,
  getTests,
  LabTrendResponse,
  TestCatalogResponse
} from "../api/client";
import type { DevUser } from "../api/client";

type TrendPageProps = {
  devUser: DevUser;
};

type ChartPoint = {
  observedAt: string;
  numericValue: number;
  unit: string;
  sourceType: string | null;
  reportId: string | null;
  reportOriginalFilename: string | null;
  labName: string | null;
  reportDate: string | null;
  parsedObservationId: string | null;
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

function formatValueWithUnit(value: number | null | undefined, unit: string | null | undefined) {
  const formattedValue = formatNumber(value);

  if (formattedValue === "N/A" || !unit) {
    return formattedValue;
  }

  return `${formattedValue} ${unit}`;
}

function formatDate(value: string | null | undefined) {
  if (!value) {
    return "N/A";
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

function sortTrendPoints(points: ChartPoint[]) {
  return [...points].sort(
    (left, right) =>
      new Date(left.observedAt).getTime() - new Date(right.observedAt).getTime()
  );
}

function getSummary(trend: LabTrendResponse, points: ChartPoint[]): Summary {
  const latestPoint = points[points.length - 1];
  const previousPoint = points[points.length - 2];
  const latestValue = trend.latestValue ?? latestPoint?.numericValue ?? null;
  const previousValue =
    trend.previousValue ?? previousPoint?.numericValue ?? null;
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

function normalizeSourceType(sourceType: string | null | undefined) {
  if (sourceType === "REPORT" || sourceType === "MANUAL") {
    return sourceType;
  }

  return null;
}

function formatTooltip(point: ChartPoint, fallbackUnit: string) {
  const unit = point.unit || fallbackUnit;
  const sourceType = normalizeSourceType(point.sourceType);
  const lines = [
    formatDate(point.observedAt),
    formatValueWithUnit(point.numericValue, unit)
  ];

  if (sourceType === "REPORT") {
    lines.push("Source: Report");
    lines.push(`Lab: ${point.labName || "Not provided"}`);
    lines.push(`Report: ${point.reportOriginalFilename || "Not provided"}`);
    lines.push(`Report date: ${formatDate(point.reportDate)}`);
  } else if (sourceType === "MANUAL") {
    lines.push("Source: Manual entry");
  } else {
    lines.push("Source: Unknown");
  }

  return lines.join("\n");
}

function toChartPoint(point: LabTrendResponse["points"][number], fallbackUnit: string): ChartPoint | null {
  const observedAt = point.observedAt ?? point.date;
  const rawValue = point.numericValue ?? point.value;
  const numericValue = Number(rawValue);

  if (!observedAt || !Number.isFinite(numericValue)) {
    return null;
  }

  return {
    observedAt,
    numericValue,
    unit: point.unit ?? fallbackUnit,
    sourceType: point.sourceType ?? null,
    reportId: point.reportId ?? null,
    reportOriginalFilename: point.reportOriginalFilename ?? null,
    labName: point.labName ?? null,
    reportDate: point.reportDate ?? null,
    parsedObservationId: point.parsedObservationId ?? null
  };
}

function TrendLineChart({ points, unit }: { points: ChartPoint[]; unit: string }) {
  const width = 720;
  const height = 320;
  const padding = 52;
  const values = points.map((point) => point.numericValue);
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
      ((point.numericValue - minValue) / valueRange) * (height - padding * 2);

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
        {plottedPoints.map((point, index) => (
          <g key={`${point.observedAt}-${point.numericValue}-${index}`}>
            <title>{formatTooltip(point, unit)}</title>
            <circle className="chart-point" cx={point.x} cy={point.y} r="6" />
            <text className="chart-point-label" x={point.x} y={point.y - 12}>
              {formatNumber(point.numericValue)}
            </text>
          </g>
        ))}
      </svg>
      <div className="chart-dates">
        {points.map((point, index) => (
          <span key={`${point.observedAt}-${index}`}>{formatDate(point.observedAt)}</span>
        ))}
      </div>
    </div>
  );
}

function TrendPointsTable({ points, fallbackUnit }: { points: ChartPoint[]; fallbackUnit: string }) {
  return (
    <section className="trend-points-section" aria-labelledby="trend-points-title">
      <div className="trend-points-header">
        <h3 id="trend-points-title">Trend points</h3>
      </div>
      <div className="table-scroll">
        <table className="reports-table trend-points-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Value</th>
              <th>Unit</th>
              <th>Source</th>
              <th>Lab / Report</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point, index) => {
              const sourceType = normalizeSourceType(point.sourceType);
              const unit = point.unit || fallbackUnit;

              return (
                <tr key={`${point.observedAt}-${point.numericValue}-${point.parsedObservationId ?? index}`}>
                  <td className="nowrap-cell">{formatDate(point.observedAt)}</td>
                  <td>{formatNumber(point.numericValue)}</td>
                  <td>{unit || "N/A"}</td>
                  <td>
                    {sourceType === "REPORT" ? (
                      <span className="status-badge source-badge-report">REPORT</span>
                    ) : null}
                    {sourceType === "MANUAL" ? (
                      <span className="status-badge source-badge-manual">MANUAL</span>
                    ) : null}
                    {!sourceType ? (
                      <span className="status-badge status-badge-neutral">Unknown source</span>
                    ) : null}
                  </td>
                  <td>
                    {sourceType === "REPORT" ? (
                      <div className="trend-source-detail">
                        <span className="trend-source-lab">
                          {point.labName || "Unknown lab"}
                        </span>
                        {point.reportId && point.reportOriginalFilename ? (
                          <Link to={`/reports/${point.reportId}`}>
                            {point.reportOriginalFilename}
                          </Link>
                        ) : (
                          <span>{point.reportOriginalFilename || "Report file unavailable"}</span>
                        )}
                      </div>
                    ) : null}
                    {sourceType === "MANUAL" ? "Manual entry" : null}
                    {!sourceType ? "Unknown source" : null}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function TrendPage({ devUser }: TrendPageProps) {
  const { testId } = useParams();
  const [trend, setTrend] = useState<LabTrendResponse | null>(null);
  const [tests, setTests] = useState<TestCatalogResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const isClinician = devUser.role === "CLINICIAN";
  const selectedPatientId = isClinician ? getSelectedAssignedPatientId() : null;

  useEffect(() => {
    let isCurrent = true;

    if (!testId) {
      setTrend(null);
      setErrorMessage("No test was selected.");
      setIsLoading(false);
      return;
    }

    if (isClinician && !selectedPatientId) {
      setTrend(null);
      setTests([]);
      setErrorMessage("Select an assigned patient first.");
      setIsLoading(false);
      return () => {
        isCurrent = false;
      };
    }

    setIsLoading(true);
    setErrorMessage("");

    Promise.allSettled([getLabTrend(testId, selectedPatientId), getTests()])
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
          formatLoadErrorMessage(error, "Unable to load trend data.")
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
  }, [isClinician, selectedPatientId, testId]);

  const selectedTest = useMemo(
    () => tests.find((test) => test.id === (trend?.testId ?? testId)),
    [testId, tests, trend?.testId]
  );
  const trendName =
    getCatalogTestName(selectedTest) || trend?.testName?.trim() || "Selected Test";
  const trendUnit = trend?.unit ?? selectedTest?.defaultUnit ?? "";
  const trendTitle = trend ? `${trendName} Trend` : "Trend";
  const points = useMemo(() => {
    const mappedPoints =
      trend?.points
        .map((point) => toChartPoint(point, trendUnit))
        .filter((point): point is ChartPoint => point !== null) ?? [];

    return sortTrendPoints(mappedPoints);
  }, [trend?.points, trendUnit]);
  const summary = useMemo(
    () => (trend ? getSummary(trend, points) : null),
    [points, trend]
  );

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

          {points.length > 0 ? (
            <TrendPointsTable points={points} fallbackUnit={trendUnit} />
          ) : null}
        </>
      ) : null}
    </section>
  );
}

export default TrendPage;

import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  clearSelectedAssignedPatientId,
  formatLoadErrorMessage,
  getAssignedPatients,
  getPatientClinicianAccess,
  getSelectedAssignedPatientId,
  setSelectedAssignedPatientId
} from "../api/client";
import type {
  AssignedPatientResponse,
  DevUser,
  PatientClinicianAccessResponse
} from "../api/client";
import { getPatientVaultService } from "../vault";
import { getPatientVaultMode } from "../vault/config";
import type {
  VaultAuditEvent,
  VaultParsedObservationReviewItem,
  VaultReportDocument
} from "../vault";

type DashboardAction = {
  title: string;
  description: string;
  to: string;
  tone: string;
  meta: string;
};

type SummaryCard = {
  label: string;
  value: string;
  detail: string;
  detailTitle?: string;
  truncateDetail?: boolean;
  to?: string;
  tone?: "primary" | "sand" | "neutral";
};

type PatientDashboardData = {
  reports: VaultReportDocument[];
  reviewItems: VaultParsedObservationReviewItem[];
  careTeamAccess: PatientClinicianAccessResponse[];
  activityEvents: VaultAuditEvent[];
};

type PatientDashboardErrors = {
  reports?: string;
  review?: string;
  careTeam?: string;
  activity?: string;
};

const patientDashboardActions: DashboardAction[] = [
  {
    title: "Upload report",
    description: "Add a diagnostic report with report date and lab metadata.",
    to: "/upload",
    tone: "primary",
    meta: "New report"
  },
  {
    title: "Add manual observation",
    description: "Enter a lab observation directly for trend tracking.",
    to: "/observations/new",
    tone: "sand",
    meta: "Manual entry"
  },
  {
    title: "Review extracted results",
    description: "Resolve parsed observations waiting for confirmation.",
    to: "/review",
    tone: "teal",
    meta: "Review queue"
  },
  {
    title: "Manage care team",
    description: "Grant or revoke clinician access to your reports.",
    to: "/care-team",
    tone: "teal",
    meta: "Sharing"
  }
];

const clinicianDashboardActions: DashboardAction[] = [
  {
    title: "View assigned patients",
    description: "Choose a patient connected through active access.",
    to: "/patients",
    tone: "primary",
    meta: "Patient panel"
  },
  {
    title: "Open patient reports",
    description: "View reports for the selected assigned patient.",
    to: "/reports",
    tone: "teal",
    meta: "Reports"
  },
  {
    title: "Open review queue",
    description: "Review parsed observations for the selected patient.",
    to: "/review",
    tone: "sand",
    meta: "Needs review"
  },
  {
    title: "View trends",
    description: "Open trend views for the selected assigned patient.",
    to: "/trends",
    tone: "teal",
    meta: "Trends"
  }
];

const ACTION_LABELS: Record<string, string> = {
  CLINICIAN_ACCESS_GRANTED: "Clinician access granted",
  CLINICIAN_ACCESS_REVOKED: "Clinician access revoked",
  REPORT_UPLOADED: "Report uploaded",
  PARSED_OBSERVATION_CONFIRMED: "Observation confirmed",
  PARSED_OBSERVATION_REJECTED: "Observation rejected",
  REPORT_DELETED: "Report deleted"
};

type DashboardPageProps = {
  devUser: DevUser;
};

function formatOptionalDate(value: string | null | undefined) {
  if (!value) {
    return "No report date";
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

function formatReportCount(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return "Reports unavailable";
  }

  return `${value.toLocaleString()} ${value === 1 ? "report" : "reports"}`;
}

function formatLabel(value: string | null) {
  if (!value) {
    return "Not provided";
  }

  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function formatAction(action: string) {
  return ACTION_LABELS[action] ?? formatLabel(action);
}

function getLatestReport(reports: VaultReportDocument[]) {
  return [...reports].sort((first, second) => {
    const firstTime = new Date(first.uploadedAt ?? "").getTime();
    const secondTime = new Date(second.uploadedAt ?? "").getTime();
    return secondTime - firstTime;
  })[0];
}

function getObservationName(observation: VaultParsedObservationReviewItem) {
  return observation.testName || "Parsed observation";
}

function buildPatientSummaryCards(
  data: PatientDashboardData,
  errors: PatientDashboardErrors,
  isLoading: boolean,
  isLocalVaultMode: boolean
): SummaryCard[] {
  const latestReport = getLatestReport(data.reports);
  const activeCareTeamCount = data.careTeamAccess.filter(
    (access) => access.status === "ACTIVE"
  ).length;

  return [
    {
      label: "Reports",
      value: isLoading
        ? "Loading"
        : errors.reports
          ? "Open"
          : data.reports.length.toLocaleString(),
      detail: isLoading
        ? "Loading reports..."
        : errors.reports
        ? "Open your report library"
        : latestReport
          ? `Latest: ${latestReport.originalFilename}`
          : "No reports uploaded yet",
      detailTitle: latestReport?.originalFilename,
      truncateDetail: Boolean(latestReport),
      to: "/reports",
      tone: "primary"
    },
    {
      label: "Results waiting for review",
      value: isLoading
        ? "Loading"
        : errors.review
          ? "Open"
          : data.reviewItems.length.toLocaleString(),
      detail: isLoading
        ? "Loading review queue..."
        : isLocalVaultMode
        ? "Local extraction coming soon"
        : errors.review
        ? "Open the review queue"
        : data.reviewItems.length === 0
          ? "No results waiting for review"
          : "Review extracted observations",
      to: "/review",
      tone: "sand"
    },
    {
      label: "Trends",
      value: "Open",
      detail: "View confirmed observations over time",
      to: "/trends",
      tone: "neutral"
    },
    {
      label: "Care team access",
      value: isLoading
        ? "Loading"
        : errors.careTeam
          ? "Manage"
          : activeCareTeamCount.toLocaleString(),
      detail: isLoading
        ? "Loading care team..."
        : errors.careTeam
        ? "Manage clinician access"
        : activeCareTeamCount === 1
          ? "1 active clinician"
          : `${activeCareTeamCount.toLocaleString()} active clinicians`,
      to: "/care-team",
      tone: "neutral"
    }
  ];
}

function DashboardSummaryGrid({ cards }: { cards: SummaryCard[] }) {
  return (
    <div className="dashboard-summary-grid" aria-label="Dashboard summary">
      {cards.map((card) => {
        const content = (
          <>
            <span className="dashboard-summary-label">{card.label}</span>
            <strong className="dashboard-summary-value">{card.value}</strong>
            <span
              className={
                card.truncateDetail
                  ? "dashboard-summary-detail text-truncate"
                  : "dashboard-summary-detail"
              }
              title={card.detailTitle}
            >
              {card.detail}
            </span>
          </>
        );

        if (!card.to) {
          return (
            <article
              className={`dashboard-summary-card dashboard-summary-${card.tone ?? "neutral"}`}
              key={card.label}
            >
              {content}
            </article>
          );
        }

        return (
          <Link
            className={`dashboard-summary-card dashboard-summary-link dashboard-summary-${card.tone ?? "neutral"}`}
            key={card.label}
            to={card.to}
          >
            {content}
          </Link>
        );
      })}
    </div>
  );
}

function PatientNeedsReviewPanel({
  errorMessage,
  isLoading,
  isLocalVaultMode,
  reviewItems
}: {
  errorMessage: string;
  isLoading: boolean;
  isLocalVaultMode: boolean;
  reviewItems: VaultParsedObservationReviewItem[];
}) {
  const visibleItems = reviewItems.slice(0, 4);

  return (
    <section className="dashboard-panel dashboard-priority-panel">
      <div className="dashboard-panel-header">
        <div>
          <p className="eyebrow">Priority</p>
          <h3>Needs review</h3>
        </div>
        <Link className="button-link secondary" to="/review">
          Open Review Queue
        </Link>
      </div>

      {isLoading ? (
        <p className="status-message">Loading review queue...</p>
      ) : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && visibleItems.length === 0 ? (
        <p className="dashboard-empty-state">
          {isLocalVaultMode
            ? "Automatic extraction is not available yet. Add observations manually or upload reports as local records."
            : "No results waiting for review."}
        </p>
      ) : null}

      {!isLoading && !errorMessage && visibleItems.length > 0 ? (
        <div className="dashboard-review-list">
          {visibleItems.map((observation) => (
            <Link
              className="dashboard-review-item"
              key={observation.parsedObservationId}
              title={observation.reportOriginalFilename ?? undefined}
              to={observation.reportId ? `/reports/${observation.reportId}` : "/review"}
            >
              <span>
                <strong>{getObservationName(observation)}</strong>
                <small className="text-truncate">
                  {observation.reportOriginalFilename ?? "Source report unavailable"}
                </small>
              </span>
              <span>{formatOptionalDate(observation.observedAt)}</span>
            </Link>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function RecentActivityPanel({
  activityEvents,
  errorMessage,
  isLoading,
  isLocalVaultMode
}: {
  activityEvents: VaultAuditEvent[];
  errorMessage: string;
  isLoading: boolean;
  isLocalVaultMode: boolean;
}) {
  const visibleEvents = activityEvents.slice(0, 5);

  return (
    <section className="dashboard-panel">
      <div className="dashboard-panel-header">
        <div>
          <p className="eyebrow">Recent</p>
          <h3>Activity</h3>
        </div>
        <Link className="button-link secondary" to="/activity">
          View Activity
        </Link>
      </div>

      {isLoading ? <p className="status-message">Loading activity...</p> : null}

      {!isLoading && errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!isLoading && !errorMessage && visibleEvents.length === 0 ? (
        <p className="dashboard-empty-state">
          {isLocalVaultMode
            ? "Local activity history will be available as vault features are completed."
            : "No activity yet."}
        </p>
      ) : null}

      {!isLoading && !errorMessage && visibleEvents.length > 0 ? (
        <div className="dashboard-activity-list">
          {visibleEvents.map((event) => {
            const eventMeta = `${formatLabel(event.actorRole)} - ${formatLabel(
              event.resourceTypeName
            )}`;

            return (
              <article
                className="dashboard-activity-item"
                key={event.auditEventId}
              >
                <span>
                  <strong>{formatAction(event.action)}</strong>
                  <small className="text-truncate" title={eventMeta}>
                    {eventMeta}
                  </small>
                </span>
                <time dateTime={event.createdAt}>
                  {formatDateTime(event.createdAt)}
                </time>
              </article>
            );
          })}
        </div>
      ) : null}
    </section>
  );
}

function DashboardActionGrid({ actions }: { actions: DashboardAction[] }) {
  return (
    <div className="dashboard-action-grid" aria-label="Dashboard actions">
      {actions.map((action) => (
        <Link
          className={`dashboard-action-card dashboard-action-card-${action.tone}`}
          key={action.title}
          to={action.to}
        >
          <span className="dashboard-action-accent" aria-hidden="true" />
          <span className="dashboard-action-meta">{action.meta}</span>
          <span className="dashboard-action-title">{action.title}</span>
          <span className="dashboard-action-description">
            {action.description}
          </span>
          <span className="dashboard-action-cta">Open</span>
        </Link>
      ))}
    </div>
  );
}

function PatientDashboard() {
  const [data, setData] = useState<PatientDashboardData>({
    reports: [],
    reviewItems: [],
    careTeamAccess: [],
    activityEvents: []
  });
  const [errors, setErrors] = useState<PatientDashboardErrors>({});
  const [isLoading, setIsLoading] = useState(true);
  const isLocalVaultMode = getPatientVaultMode() === "local";

  useEffect(() => {
    let isCurrent = true;

    setIsLoading(true);
    setErrors({});

    Promise.allSettled([
      getPatientVaultService().listReports(),
      getPatientVaultService().listParsedObservations({
        status: "NEEDS_REVIEW"
      }),
      isLocalVaultMode
        ? Promise.resolve([] as PatientClinicianAccessResponse[])
        : getPatientClinicianAccess(),
      getPatientVaultService().listAuditEvents()
    ])
      .then(([reportsResult, reviewResult, careTeamResult, activityResult]) => {
        if (!isCurrent) {
          return;
        }

        const nextData: PatientDashboardData = {
          reports:
            reportsResult.status === "fulfilled" ? reportsResult.value : [],
          reviewItems:
            reviewResult.status === "fulfilled" ? reviewResult.value : [],
          careTeamAccess:
            careTeamResult.status === "fulfilled" ? careTeamResult.value : [],
          activityEvents:
            activityResult.status === "fulfilled" ? activityResult.value : []
        };
        const nextErrors: PatientDashboardErrors = {};

        if (reportsResult.status === "rejected") {
          nextErrors.reports = formatLoadErrorMessage(
            reportsResult.reason,
            "Unable to load reports."
          );
        }

        if (reviewResult.status === "rejected") {
          nextErrors.review = formatLoadErrorMessage(
            reviewResult.reason,
            "Unable to load review queue."
          );
        }

        if (careTeamResult.status === "rejected") {
          nextErrors.careTeam = formatLoadErrorMessage(
            careTeamResult.reason,
            "Unable to load care team access."
          );
        }

        if (activityResult.status === "rejected") {
          nextErrors.activity = formatLoadErrorMessage(
            activityResult.reason,
            "Unable to load activity."
          );
        }

        setData(nextData);
        setErrors(nextErrors);
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoading(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [isLocalVaultMode]);

  return (
    <section className="dashboard-page">
      <div className="dashboard-hero">
        <div>
          <p className="eyebrow">SoveraHealth</p>
          <h2 className="dashboard-title">Welcome back</h2>
          <p className="dashboard-subtitle">
            Track your diagnostic reports, review extracted results, and follow
            trends over time.
          </p>
        </div>
        <div className="dashboard-hero-actions">
          <Link className="button-link" to="/upload">
            Upload report
          </Link>
          <Link className="button-link secondary" to="/review">
            Review queue
          </Link>
        </div>
      </div>

      <DashboardSummaryGrid
        cards={buildPatientSummaryCards(
          data,
          errors,
          isLoading,
          isLocalVaultMode
        )}
      />

      <div className="dashboard-main-grid">
        <PatientNeedsReviewPanel
          errorMessage={errors.review ?? ""}
          isLoading={isLoading}
          isLocalVaultMode={isLocalVaultMode}
          reviewItems={data.reviewItems}
        />
        <RecentActivityPanel
          activityEvents={data.activityEvents}
          errorMessage={errors.activity ?? ""}
          isLoading={isLoading}
          isLocalVaultMode={isLocalVaultMode}
        />
      </div>

      <section className="dashboard-panel">
        <div className="dashboard-panel-header">
          <div>
            <p className="eyebrow">Next steps</p>
            <h3>Quick actions</h3>
          </div>
        </div>
        <DashboardActionGrid actions={patientDashboardActions} />
      </section>
    </section>
  );
}

function ClinicianDashboard() {
  const [assignedPatients, setAssignedPatients] = useState<
    AssignedPatientResponse[]
  >([]);
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(
    getSelectedAssignedPatientId
  );
  const [isLoadingPatients, setIsLoadingPatients] = useState(true);
  const [patientsErrorMessage, setPatientsErrorMessage] = useState("");

  useEffect(() => {
    let isCurrent = true;

    setIsLoadingPatients(true);
    setPatientsErrorMessage("");

    getAssignedPatients()
      .then((patients) => {
        if (!isCurrent) {
          return;
        }

        setAssignedPatients(patients);

        const storedPatientId = getSelectedAssignedPatientId();
        const storedPatientExists = patients.some(
          (patient) => patient.patientId === storedPatientId
        );
        const nextSelectedPatientId =
          patients.length === 1
            ? patients[0].patientId
            : storedPatientExists
              ? storedPatientId
              : null;

        setSelectedPatientId(nextSelectedPatientId);

        if (nextSelectedPatientId) {
          setSelectedAssignedPatientId(nextSelectedPatientId);
        } else {
          clearSelectedAssignedPatientId();
        }
      })
      .catch((error: unknown) => {
        if (!isCurrent) {
          return;
        }

        setPatientsErrorMessage(
          formatLoadErrorMessage(error, "Unable to load assigned patients.")
        );
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoadingPatients(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, []);

  const selectedPatient =
    assignedPatients.find((patient) => patient.patientId === selectedPatientId) ??
    null;

  function handleSelectPatient(patientId: string) {
    setSelectedPatientId(patientId);
    setSelectedAssignedPatientId(patientId);
  }

  return (
    <section className="dashboard-page">
      <div className="dashboard-hero clinician-dashboard-hero">
        <div>
          <p className="eyebrow">SoveraHealth</p>
          <h2 className="dashboard-title">Clinician workspace</h2>
          <p className="dashboard-subtitle">
            Review assigned patient reports, trends, and activity.
          </p>
        </div>
        <div className="dashboard-hero-actions">
          <Link className="button-link" to="/patients">
            View patients
          </Link>
        </div>
      </div>

      <div className="clinician-dashboard-grid">
        <section className="dashboard-panel" aria-label="Assigned patients">
          <div className="dashboard-panel-header">
            <div>
              <p className="eyebrow">Assigned</p>
              <h3>Patients</h3>
            </div>
            <span className="dashboard-count-pill">
              {assignedPatients.length.toLocaleString()}
            </span>
          </div>

          {isLoadingPatients ? (
            <p className="status-message">Loading assigned patients...</p>
          ) : null}

          {!isLoadingPatients && patientsErrorMessage ? (
            <p className="status-message error-message" role="alert">
              {patientsErrorMessage}
            </p>
          ) : null}

          {!isLoadingPatients &&
          !patientsErrorMessage &&
          assignedPatients.length === 0 ? (
            <p className="dashboard-empty-state">No assigned patients yet.</p>
          ) : null}

          {!isLoadingPatients &&
          !patientsErrorMessage &&
          assignedPatients.length > 0 ? (
            <div className="assigned-patient-list">
              {assignedPatients.map((patient) => (
                <button
                  className={
                    patient.patientId === selectedPatientId
                      ? "assigned-patient-card selected"
                      : "assigned-patient-card"
                  }
                  key={patient.patientId}
                  type="button"
                  onClick={() => handleSelectPatient(patient.patientId)}
                >
                  <span className="assigned-patient-name">
                    {patient.displayName}
                  </span>
                  <span>{patient.email}</span>
                  <span>{formatReportCount(patient.reportCount)}</span>
                  <span>
                    Latest report: {formatOptionalDate(patient.latestReportDate)}
                  </span>
                </button>
              ))}
            </div>
          ) : null}
        </section>

        <section className="dashboard-panel">
          <div className="dashboard-panel-header">
            <div>
              <p className="eyebrow">Selected patient</p>
              <h3>{selectedPatient ? selectedPatient.displayName : "Summary"}</h3>
            </div>
          </div>

          {selectedPatient ? (
            <>
              <div className="selected-patient-summary">
                <span>{selectedPatient.email}</span>
                <strong>{formatReportCount(selectedPatient.reportCount)}</strong>
                <span>
                  Latest report: {formatOptionalDate(selectedPatient.latestReportDate)}
                </span>
              </div>
              <div className="selected-patient-links">
                <Link className="button-link secondary" to="/reports">
                  Patient reports
                </Link>
                <Link className="button-link secondary" to="/review">
                  Review Queue
                </Link>
                <Link className="button-link secondary" to="/trends">
                  Trends
                </Link>
                <Link className="button-link secondary" to="/activity">
                  Activity
                </Link>
              </div>
            </>
          ) : (
            <p className="dashboard-empty-state">Select a patient to begin.</p>
          )}
        </section>
      </div>

      <section className="dashboard-panel">
        <div className="dashboard-panel-header">
          <div>
            <p className="eyebrow">Workflow</p>
            <h3>Quick actions</h3>
          </div>
        </div>
        <DashboardActionGrid actions={clinicianDashboardActions} />
      </section>
    </section>
  );
}

function DashboardPage({ devUser }: DashboardPageProps) {
  if (devUser.role === "CLINICIAN") {
    return <ClinicianDashboard />;
  }

  return <PatientDashboard />;
}

export default DashboardPage;

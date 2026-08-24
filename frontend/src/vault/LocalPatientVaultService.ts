import type { PatientVaultService } from "./PatientVaultService";
import type {
  PatientVaultManifest,
  SerializedPatientVault,
  VaultAuditEvent,
  VaultAuditEventFilters,
  VaultConfirmedObservationFilters,
  VaultObservation,
  VaultParsedObservationFilters,
  VaultParsedObservationReviewItem,
  VaultReportDocument,
  VaultReportFilters,
  VaultReportUploadMetadata,
  VaultResourceStatus,
  VaultTrendPoint
} from "./models";

const LOCAL_VAULT_SCHEMA_VERSION = "2026-08-24.local-v1";
const LOCAL_VAULT_STORAGE_KEY = "soverahealth.patientVault.local";

type LocalVaultSnapshot = {
  manifest: PatientVaultManifest;
  reports: VaultReportDocument[];
  diagnosticReports: unknown[];
  parsedObservations: VaultParsedObservationReviewItem[];
  observations: VaultObservation[];
  auditEvents: VaultAuditEvent[];
};

function nowIso() {
  return new Date().toISOString();
}

function createId(prefix: string) {
  const randomId =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

  return `${prefix}_${randomId}`;
}

function createEmptyManifest(): PatientVaultManifest {
  const timestamp = nowIso();

  return {
    resourceType: "PatientVaultManifest",
    vaultId: createId("vault"),
    patientUserId: null,
    schemaVersion: LOCAL_VAULT_SCHEMA_VERSION,
    createdAt: timestamp,
    updatedAt: timestamp,
    encrypted: false,
    storageProvider: "localStorage",
    documents: [],
    counts: {
      documentReferences: 0,
      diagnosticReports: 0,
      observations: 0,
      reviewItems: 0,
      auditEvents: 0
    }
  };
}

function createEmptySnapshot(): LocalVaultSnapshot {
  return {
    manifest: createEmptyManifest(),
    reports: [],
    diagnosticReports: [],
    parsedObservations: [],
    observations: [],
    auditEvents: []
  };
}

function assertBrowserStorageAvailable() {
  if (typeof window === "undefined" || !window.localStorage) {
    throw new Error("Local patient vault requires browser localStorage.");
  }
}

function byNewestCreatedAt<T extends { createdAt?: string | null }>(left: T, right: T) {
  return Date.parse(right.createdAt ?? "") - Date.parse(left.createdAt ?? "");
}

function updateSnapshotManifest(snapshot: LocalVaultSnapshot): LocalVaultSnapshot {
  const updatedAt = nowIso();

  return {
    ...snapshot,
    manifest: {
      ...snapshot.manifest,
      updatedAt,
      encrypted: false,
      storageProvider: "localStorage",
      documents: snapshot.reports.map((report) => ({
        documentReferenceId:
          report.documentReferenceId ?? report.reportId ?? createId("document"),
        diagnosticReportId: report.diagnosticReportId ?? null,
        encryptedBlobRef: report.encryptedBlobRef ?? null,
        contentType: report.contentType ?? null,
        sha256: report.sha256 ?? null
      })),
      counts: {
        documentReferences: snapshot.reports.length,
        diagnosticReports: snapshot.diagnosticReports.length,
        observations: snapshot.observations.length,
        reviewItems: snapshot.parsedObservations.length,
        auditEvents: snapshot.auditEvents.length
      }
    }
  };
}

function parseSnapshot(serializedVault: string): LocalVaultSnapshot {
  const parsed = JSON.parse(serializedVault) as Partial<LocalVaultSnapshot>;

  return updateSnapshotManifest({
    manifest: parsed.manifest ?? createEmptyManifest(),
    reports: parsed.reports ?? [],
    diagnosticReports: parsed.diagnosticReports ?? [],
    parsedObservations: parsed.parsedObservations ?? [],
    observations: parsed.observations ?? [],
    auditEvents: parsed.auditEvents ?? []
  });
}

function matchesPatient(patientUserId: string | null, patientId?: string | null) {
  return !patientId || patientUserId === patientId;
}

export class LocalPatientVaultService implements PatientVaultService {
  private readonly storageKey: string;

  constructor(storageKey = LOCAL_VAULT_STORAGE_KEY) {
    this.storageKey = storageKey;
  }

  async getManifest() {
    return this.readSnapshot().manifest;
  }

  async listReports(filters: VaultReportFilters = {}) {
    const snapshot = this.readSnapshot();
    return snapshot.reports
      .filter((report) => matchesPatient(report.patientUserId, filters.patientId))
      .sort(byNewestCreatedAtReport);
  }

  async getReport(reportId: string) {
    return (
      this.readSnapshot().reports.find((report) => report.reportId === reportId) ??
      null
    );
  }

  async saveReport(report: VaultReportDocument) {
    const snapshot = this.readSnapshot();
    const reportToSave: VaultReportDocument = {
      ...report,
      reportId: report.reportId || createId("report"),
      uploadedAt: report.uploadedAt ?? nowIso()
    };

    snapshot.reports = upsertBy(
      snapshot.reports,
      reportToSave,
      (candidate) => candidate.reportId === reportToSave.reportId
    );
    this.writeSnapshot(snapshot);
    return reportToSave;
  }

  async uploadReport(file: File, metadata: VaultReportUploadMetadata = {}) {
    return this.saveReport({
      resourceType: "DocumentReference",
      reportId: createId("report"),
      patientUserId: null,
      documentReferenceId: createId("document"),
      diagnosticReportId: null,
      originalFilename: file.name,
      contentType: file.type || "application/pdf",
      encryptedBlobRef: null,
      sha256: null,
      labName: metadata.labName ?? null,
      reportDate: metadata.reportDate ?? null,
      uploadedAt: nowIso(),
      status: "UPLOADED",
      sourceType: "UPLOAD"
    });
  }

  async deleteReport(reportId: string) {
    const snapshot = this.readSnapshot();
    snapshot.reports = snapshot.reports.filter(
      (report) => report.reportId !== reportId
    );
    snapshot.parsedObservations = snapshot.parsedObservations.filter(
      (item) => item.reportId !== reportId
    );
    this.writeSnapshot(snapshot);
  }

  async listParsedObservations(filters: VaultParsedObservationFilters = {}) {
    const snapshot = this.readSnapshot();

    return snapshot.parsedObservations
      .filter((item) => matchesPatient(item.patientUserId, filters.patientId))
      .filter((item) => !filters.reportId || item.reportId === filters.reportId)
      .filter((item) => !filters.status || item.status === filters.status)
      .sort(byNewestCreatedAt);
  }

  async saveParsedObservation(item: VaultParsedObservationReviewItem) {
    const snapshot = this.readSnapshot();
    const timestamp = nowIso();
    const itemToSave: VaultParsedObservationReviewItem = {
      ...item,
      parsedObservationId: item.parsedObservationId || createId("parsed"),
      createdAt: item.createdAt || timestamp,
      updatedAt: timestamp
    };

    snapshot.parsedObservations = upsertBy(
      snapshot.parsedObservations,
      itemToSave,
      (candidate) =>
        candidate.parsedObservationId === itemToSave.parsedObservationId
    );
    this.writeSnapshot(snapshot);
    return itemToSave;
  }

  async updateParsedObservationStatus(id: string, status: VaultResourceStatus) {
    const snapshot = this.readSnapshot();
    const existingItem = snapshot.parsedObservations.find(
      (item) => item.parsedObservationId === id
    );

    if (!existingItem) {
      throw new Error("Parsed observation was not found in the local vault.");
    }

    const updatedItem: VaultParsedObservationReviewItem = {
      ...existingItem,
      status,
      updatedAt: nowIso()
    };

    snapshot.parsedObservations = upsertBy(
      snapshot.parsedObservations,
      updatedItem,
      (candidate) => candidate.parsedObservationId === id
    );
    this.writeSnapshot(snapshot);
    return updatedItem;
  }

  async listConfirmedObservations(
    filters: VaultConfirmedObservationFilters = {}
  ) {
    const snapshot = this.readSnapshot();

    return snapshot.observations
      .filter((observation) =>
        matchesPatient(observation.patientUserId, filters.patientId)
      )
      .filter(
        (observation) => !filters.testId || observation.testId === filters.testId
      )
      .sort(byNewestCreatedAt);
  }

  async saveConfirmedObservation(observation: VaultObservation) {
    const snapshot = this.readSnapshot();
    const timestamp = nowIso();
    const observationToSave: VaultObservation = {
      ...observation,
      observationId: observation.observationId || createId("observation"),
      status: "CONFIRMED",
      createdAt: observation.createdAt || timestamp,
      updatedAt: timestamp
    };

    snapshot.observations = upsertBy(
      snapshot.observations,
      observationToSave,
      (candidate) => candidate.observationId === observationToSave.observationId
    );
    this.writeSnapshot(snapshot);
    return observationToSave;
  }

  async listTrendPoints(testId: string) {
    const snapshot = this.readSnapshot();

    return snapshot.observations
      .filter((observation) => observation.testId === testId)
      .filter((observation) => observation.status === "CONFIRMED")
      .map(mapObservationToTrendPoint)
      .sort((left, right) => {
        return Date.parse(left.observedAt ?? "") - Date.parse(right.observedAt ?? "");
      });
  }

  async listAuditEvents(filters: VaultAuditEventFilters = {}) {
    const snapshot = this.readSnapshot();
    const limit = filters.limit ?? 50;

    return snapshot.auditEvents
      .filter((event) => matchesPatient(event.patientUserId, filters.patientId))
      .filter((event) => !filters.action || event.action === filters.action)
      .filter(
        (event) =>
          !filters.resourceType || event.resourceTypeName === filters.resourceType
      )
      .sort(byNewestCreatedAt)
      .slice(0, limit);
  }

  async recordAuditEvent(event: VaultAuditEvent) {
    const snapshot = this.readSnapshot();
    const eventToSave: VaultAuditEvent = {
      ...event,
      auditEventId: event.auditEventId || createId("audit"),
      createdAt: event.createdAt || nowIso()
    };

    snapshot.auditEvents = upsertBy(
      snapshot.auditEvents,
      eventToSave,
      (candidate) => candidate.auditEventId === eventToSave.auditEventId
    );
    this.writeSnapshot(snapshot);
    return eventToSave;
  }

  async exportVault(): Promise<SerializedPatientVault> {
    return JSON.stringify(this.readSnapshot());
  }

  async importVault(serializedVault: SerializedPatientVault) {
    const snapshot = parseSnapshot(serializedVault);
    this.writeSnapshot(snapshot);
  }

  private readSnapshot() {
    assertBrowserStorageAvailable();
    const serializedSnapshot = window.localStorage.getItem(this.storageKey);

    if (!serializedSnapshot) {
      return createEmptySnapshot();
    }

    try {
      return parseSnapshot(serializedSnapshot);
    } catch {
      throw new Error("Local patient vault data is not valid JSON.");
    }
  }

  private writeSnapshot(snapshot: LocalVaultSnapshot) {
    assertBrowserStorageAvailable();
    window.localStorage.setItem(
      this.storageKey,
      JSON.stringify(updateSnapshotManifest(snapshot))
    );
  }
}

function byNewestCreatedAtReport(
  left: VaultReportDocument,
  right: VaultReportDocument
) {
  return (
    Date.parse(right.uploadedAt ?? "") - Date.parse(left.uploadedAt ?? "")
  );
}

function upsertBy<T>(
  items: T[],
  item: T,
  matchesItem: (candidate: T) => boolean
) {
  const existingIndex = items.findIndex(matchesItem);

  if (existingIndex === -1) {
    return [...items, item];
  }

  return items.map((candidate, index) =>
    index === existingIndex ? item : candidate
  );
}

function mapObservationToTrendPoint(
  observation: VaultObservation
): VaultTrendPoint {
  return {
    trendPointId: observation.observationId,
    observationId: observation.observationId,
    parsedObservationId: observation.parsedObservationId,
    testId: observation.testId ?? "",
    testName: observation.testName,
    observedAt: observation.observedAt,
    valueText: observation.valueText,
    numericValue: observation.numericValue,
    unit: observation.unit,
    sourceType: observation.sourceType,
    reportId: observation.reportId,
    reportOriginalFilename: observation.reportOriginalFilename,
    labName: observation.labName,
    reportDate: observation.reportDate
  };
}

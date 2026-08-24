import type { PatientVaultService } from "./PatientVaultService";
import {
  decryptJsonWithKey,
  deriveKeyFromStoredSalt,
  encryptJson,
  encryptJsonWithKey
} from "./crypto/vaultCrypto";
import {
  clearEncryptedVault,
  loadEncryptedVaultBlob,
  parseSerializedEncryptedVault,
  saveEncryptedVaultBlob,
  hasEncryptedVaultBlob
} from "./local/encryptedVaultStorage";
import type {
  PatientVaultManifest,
  SerializedPatientVault,
  VaultAuditEvent,
  VaultAuditEventFilters,
  VaultConfirmedObservationUpdate,
  VaultConfirmedObservationFilters,
  VaultObservation,
  VaultParsedObservationFilters,
  VaultParsedObservationReviewItem,
  VaultParsedObservationUpdate,
  VaultReportDocument,
  VaultReportFilters,
  VaultReportUploadMetadata,
  VaultResourceStatus,
  VaultTrend,
  VaultTrendFilters,
  VaultTrendTest,
  VaultTrendPoint
} from "./models";

const LOCAL_VAULT_SCHEMA_VERSION = "2026-08-24.local-v1";

type LocalVaultSnapshot = {
  manifest: PatientVaultManifest;
  reports: VaultReportDocument[];
  parsedObservations: VaultParsedObservationReviewItem[];
  confirmedObservations: VaultObservation[];
  auditEvents: VaultAuditEvent[];
};

type StoredLocalVaultSnapshot = Partial<LocalVaultSnapshot> & {
  observations?: VaultObservation[];
};

let unlockedSnapshot: LocalVaultSnapshot | null = null;
let activeEncryptionKey: CryptoKey | null = null;
let activeSalt: string | null = null;

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
    encrypted: true,
    storageProvider: "localStorage:aes-gcm",
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
    parsedObservations: [],
    confirmedObservations: [],
    auditEvents: []
  };
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
      encrypted: true,
      storageProvider: "localStorage:aes-gcm",
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
        diagnosticReports: 0,
        observations: snapshot.confirmedObservations.length,
        reviewItems: snapshot.parsedObservations.length,
        auditEvents: snapshot.auditEvents.length
      }
    }
  };
}

function normalizeSnapshot(parsed: StoredLocalVaultSnapshot): LocalVaultSnapshot {
  return updateSnapshotManifest({
    manifest: parsed.manifest ?? createEmptyManifest(),
    reports: parsed.reports ?? [],
    parsedObservations: parsed.parsedObservations ?? [],
    confirmedObservations:
      parsed.confirmedObservations ?? parsed.observations ?? [],
    auditEvents: parsed.auditEvents ?? []
  });
}

function matchesPatient(patientUserId: string | null, patientId?: string | null) {
  return !patientId || patientUserId === patientId;
}

function localVaultUnsupported(operation: string): Error {
  return new Error(
    `${operation} is not implemented for local patient vault mode yet. Local mode is encrypted but development-only.`
  );
}

function isInDateRange(
  observedAt: string | null,
  filters: VaultTrendFilters | VaultConfirmedObservationFilters
) {
  if (!observedAt) {
    return false;
  }

  if (filters.startDate && observedAt < filters.startDate) {
    return false;
  }

  if (filters.endDate && observedAt > filters.endDate) {
    return false;
  }

  return true;
}

export function isLocalPatientVaultUnlocked() {
  return unlockedSnapshot !== null && activeEncryptionKey !== null;
}

export function hasLocalPatientVault() {
  return hasEncryptedVaultBlob();
}

export async function unlockLocalPatientVault(passphrase: string) {
  if (!passphrase) {
    throw new Error("Enter a passphrase to unlock the local vault.");
  }

  const encryptedBlob = loadEncryptedVaultBlob();

  if (encryptedBlob) {
    const encryptionKey = await deriveKeyFromStoredSalt(
      passphrase,
      encryptedBlob.kdf.salt
    );
    const decryptedSnapshot = await decryptJsonWithKey<StoredLocalVaultSnapshot>(
      encryptedBlob,
      encryptionKey
    );
    unlockedSnapshot = normalizeSnapshot(decryptedSnapshot);
    activeEncryptionKey = encryptionKey;
    activeSalt = encryptedBlob.kdf.salt;
    return unlockedSnapshot.manifest;
  }

  unlockedSnapshot = createEmptySnapshot();
  const encryptedNewVault = await encryptJson(unlockedSnapshot, passphrase);
  saveEncryptedVaultBlob(encryptedNewVault);
  activeEncryptionKey = await deriveKeyFromStoredSalt(
    passphrase,
    encryptedNewVault.kdf.salt
  );
  activeSalt = encryptedNewVault.kdf.salt;
  return unlockedSnapshot.manifest;
}

export function lockLocalPatientVault() {
  unlockedSnapshot = null;
  activeEncryptionKey = null;
  activeSalt = null;
}

export function clearLocalPatientVault() {
  clearEncryptedVault();
  lockLocalPatientVault();
}

export class LocalPatientVaultService implements PatientVaultService {
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
    await this.writeSnapshot(snapshot);
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
    snapshot.confirmedObservations = snapshot.confirmedObservations.filter(
      (observation) => observation.reportId !== reportId
    );
    await this.writeSnapshot(snapshot);
  }

  async listParsedObservations(filters: VaultParsedObservationFilters = {}) {
    const snapshot = this.readSnapshot();

    return snapshot.parsedObservations
      .filter((item) => matchesPatient(item.patientUserId, filters.patientId))
      .filter((item) => !filters.reportId || item.reportId === filters.reportId)
      .filter((item) => !filters.status || item.status === filters.status)
      .sort(byNewestCreatedAt);
  }

  async getParsedObservationsForReport(reportId: string) {
    return this.listParsedObservations({ reportId });
  }

  async refreshParsedObservations(
    _reportId: string
  ): Promise<VaultParsedObservationReviewItem[]> {
    throw localVaultUnsupported("refreshParsedObservations");
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
    await this.writeSnapshot(snapshot);
    return itemToSave;
  }

  async updateParsedObservation(
    id: string,
    updates: VaultParsedObservationUpdate
  ) {
    const snapshot = this.readSnapshot();
    const existingItem = snapshot.parsedObservations.find(
      (item) => item.parsedObservationId === id
    );

    if (!existingItem) {
      throw new Error("Parsed observation was not found in the local vault.");
    }

    const updatedItem: VaultParsedObservationReviewItem = {
      ...existingItem,
      testName:
        updates.rawTestName === undefined
          ? existingItem.testName
          : updates.rawTestName,
      testId:
        updates.matchedTestId === undefined
          ? existingItem.testId
          : updates.matchedTestId,
      observedAt:
        updates.observedAt === undefined
          ? existingItem.observedAt
          : updates.observedAt,
      valueText:
        updates.rawValue === undefined ? existingItem.valueText : updates.rawValue,
      numericValue:
        updates.numericValue === undefined
          ? existingItem.numericValue
          : updates.numericValue,
      unit: updates.unit === undefined ? existingItem.unit : updates.unit,
      referenceRange:
        updates.referenceRange === undefined
          ? existingItem.referenceRange
          : updates.referenceRange,
      updatedAt: nowIso()
    };

    snapshot.parsedObservations = upsertBy(
      snapshot.parsedObservations,
      updatedItem,
      (candidate) => candidate.parsedObservationId === id
    );
    await this.writeSnapshot(snapshot);
    return updatedItem;
  }

  async confirmParsedObservation(id: string): Promise<VaultObservation> {
    const snapshot = this.readSnapshot();
    const existingItem = snapshot.parsedObservations.find(
      (item) => item.parsedObservationId === id
    );

    if (!existingItem) {
      throw new Error("Parsed observation was not found in the local vault.");
    }

    const timestamp = nowIso();
    const observation: VaultObservation = {
      resourceType: "Observation",
      observationId:
        existingItem.confirmedObservationId ?? createId("observation"),
      patientUserId: existingItem.patientUserId,
      testId: existingItem.testId,
      testName: existingItem.testName ?? "Unknown test",
      observedAt: existingItem.observedAt,
      valueText: existingItem.valueText,
      numericValue: existingItem.numericValue,
      unit: existingItem.unit,
      referenceRange: existingItem.referenceRange,
      abnormalFlag: existingItem.abnormalFlag,
      status: "CONFIRMED",
      sourceType: existingItem.reportId ? "REPORT" : "MANUAL",
      reportId: existingItem.reportId,
      reportOriginalFilename: existingItem.reportOriginalFilename,
      labName: existingItem.labName,
      reportDate: existingItem.reportDate,
      parsedObservationId: existingItem.parsedObservationId,
      createdAt: timestamp,
      updatedAt: timestamp
    };
    const updatedItem: VaultParsedObservationReviewItem = {
      ...existingItem,
      status: "CONFIRMED",
      confirmedObservationId: observation.observationId,
      updatedAt: timestamp
    };

    snapshot.confirmedObservations = upsertBy(
      snapshot.confirmedObservations,
      observation,
      (candidate) => candidate.observationId === observation.observationId
    );
    snapshot.parsedObservations = upsertBy(
      snapshot.parsedObservations,
      updatedItem,
      (candidate) => candidate.parsedObservationId === id
    );
    await this.writeSnapshot(snapshot);
    return observation;
  }

  async rejectParsedObservation(id: string) {
    return this.updateParsedObservationStatus(id, "REJECTED");
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
    await this.writeSnapshot(snapshot);
    return updatedItem;
  }

  async listConfirmedObservations(
    filters: VaultConfirmedObservationFilters = {}
  ) {
    const snapshot = this.readSnapshot();

    return snapshot.confirmedObservations
      .filter((observation) =>
        matchesPatient(observation.patientUserId, filters.patientId)
      )
      .filter(
        (observation) => !filters.testId || observation.testId === filters.testId
      )
      .filter((observation) => isInDateRange(observation.observedAt, filters))
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

    snapshot.confirmedObservations = upsertBy(
      snapshot.confirmedObservations,
      observationToSave,
      (candidate) => candidate.observationId === observationToSave.observationId
    );
    await this.writeSnapshot(snapshot);
    return observationToSave;
  }

  async updateConfirmedObservation(
    observationId: string,
    updates: VaultConfirmedObservationUpdate
  ) {
    const snapshot = this.readSnapshot();
    const existingObservation = snapshot.confirmedObservations.find(
      (observation) => observation.observationId === observationId
    );

    if (!existingObservation) {
      throw new Error("Observation was not found in the local vault.");
    }

    if (existingObservation.sourceType !== "MANUAL") {
      throw new Error("Only manual observations can be edited in local vault mode.");
    }

    const updatedObservation: VaultObservation = {
      ...existingObservation,
      ...updates,
      status: "CONFIRMED",
      sourceType: "MANUAL",
      reportId: null,
      reportOriginalFilename: null,
      labName: null,
      reportDate: null,
      parsedObservationId: null,
      updatedAt: nowIso()
    };

    snapshot.confirmedObservations = upsertBy(
      snapshot.confirmedObservations,
      updatedObservation,
      (candidate) => candidate.observationId === observationId
    );
    await this.writeSnapshot(snapshot);
    return updatedObservation;
  }

  async deleteConfirmedObservation(observationId: string) {
    const snapshot = this.readSnapshot();
    const existingObservation = snapshot.confirmedObservations.find(
      (observation) => observation.observationId === observationId
    );

    if (!existingObservation) {
      throw new Error("Observation was not found in the local vault.");
    }

    if (existingObservation.sourceType !== "MANUAL") {
      throw new Error("Only manual observations can be deleted in local vault mode.");
    }

    snapshot.confirmedObservations = snapshot.confirmedObservations.filter(
      (observation) => observation.observationId !== observationId
    );
    await this.writeSnapshot(snapshot);
  }

  async listTrendPoints(testId: string, filters: VaultTrendFilters = {}) {
    const snapshot = this.readSnapshot();

    return snapshot.confirmedObservations
      .filter((observation) => observation.testId === testId)
      .filter((observation) => observation.status === "CONFIRMED")
      .filter((observation) =>
        matchesPatient(observation.patientUserId, filters.patientId)
      )
      .filter((observation) => isInDateRange(observation.observedAt, filters))
      .map(mapObservationToTrendPoint)
      .sort((left, right) => {
        return Date.parse(left.observedAt ?? "") - Date.parse(right.observedAt ?? "");
      });
  }

  async listAvailableTrendTests(filters: VaultTrendFilters = {}) {
    const snapshot = this.readSnapshot();
    const observationsByTestId = new Map<string, VaultObservation[]>();

    for (const observation of snapshot.confirmedObservations) {
      if (
        observation.status !== "CONFIRMED" ||
        !observation.testId ||
        !matchesPatient(observation.patientUserId, filters.patientId) ||
        !isInDateRange(observation.observedAt, filters)
      ) {
        continue;
      }

      const observations = observationsByTestId.get(observation.testId) ?? [];
      observations.push(observation);
      observationsByTestId.set(observation.testId, observations);
    }

    return Array.from(observationsByTestId.entries()).map(
      ([testId, observations]): VaultTrendTest => {
        const firstObservation = observations[0];
        return {
          testId,
          canonicalName: firstObservation.testName,
          displayName: firstObservation.testName,
          name: firstObservation.testName,
          defaultUnit: firstObservation.unit,
          category: null,
          pointCount: observations.length,
          unit: firstObservation.unit
        };
      }
    );
  }

  async getTrendForTest(testId: string, filters: VaultTrendFilters = {}) {
    const points = await this.listTrendPoints(testId, filters);
    const latestPoint = points[points.length - 1];

    return {
      testId,
      testName: latestPoint?.testName ?? null,
      unit: latestPoint?.unit ?? null,
      points,
      latestValue: latestPoint?.numericValue ?? null,
      previousValue: points.length > 1 ? points[points.length - 2].numericValue : null,
      absoluteChange: null,
      percentChange: null
    } satisfies VaultTrend;
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
    await this.writeSnapshot(snapshot);
    return eventToSave;
  }

  async exportVault(): Promise<SerializedPatientVault> {
    const encryptedBlob = loadEncryptedVaultBlob();

    if (!encryptedBlob) {
      throw new Error("No encrypted local vault exists to export.");
    }

    return JSON.stringify(encryptedBlob);
  }

  async importVault(serializedVault: SerializedPatientVault) {
    const encryptedBlob = parseSerializedEncryptedVault(serializedVault);
    saveEncryptedVaultBlob(encryptedBlob);
    lockLocalPatientVault();
  }

  private readSnapshot() {
    if (!unlockedSnapshot) {
      throw new Error("Unlock the local encrypted vault before reading data.");
    }

    return unlockedSnapshot;
  }

  private async writeSnapshot(snapshot: LocalVaultSnapshot) {
    if (!activeEncryptionKey || !activeSalt) {
      throw new Error("Unlock the local encrypted vault before saving data.");
    }

    const normalizedSnapshot = updateSnapshotManifest(snapshot);
    const encryptedBlob = await encryptJsonWithKey(
      normalizedSnapshot,
      activeEncryptionKey,
      activeSalt,
      loadEncryptedVaultBlob()
    );
    saveEncryptedVaultBlob(encryptedBlob);
    unlockedSnapshot = normalizedSnapshot;
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
    reportDate: observation.reportDate,
    referenceRange: observation.referenceRange,
    abnormalFlag: observation.abnormalFlag
  };
}

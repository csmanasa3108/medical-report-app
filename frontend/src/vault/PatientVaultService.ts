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

export type PatientVaultService = {
  getManifest(): Promise<PatientVaultManifest>;

  listReports(filters?: VaultReportFilters): Promise<VaultReportDocument[]>;
  getReport(reportId: string): Promise<VaultReportDocument | null>;
  saveReport(report: VaultReportDocument): Promise<VaultReportDocument>;
  uploadReport(
    file: File,
    metadata?: VaultReportUploadMetadata
  ): Promise<VaultReportDocument>;
  deleteReport(reportId: string): Promise<void>;

  listParsedObservations(
    filters?: VaultParsedObservationFilters
  ): Promise<VaultParsedObservationReviewItem[]>;
  getParsedObservationsForReport(
    reportId: string
  ): Promise<VaultParsedObservationReviewItem[]>;
  refreshParsedObservations(
    reportId: string
  ): Promise<VaultParsedObservationReviewItem[]>;
  saveParsedObservation(
    item: VaultParsedObservationReviewItem
  ): Promise<VaultParsedObservationReviewItem>;
  updateParsedObservation(
    id: string,
    updates: VaultParsedObservationUpdate
  ): Promise<VaultParsedObservationReviewItem>;
  confirmParsedObservation(id: string): Promise<VaultObservation>;
  rejectParsedObservation(id: string): Promise<VaultParsedObservationReviewItem>;
  updateParsedObservationStatus(
    id: string,
    status: VaultResourceStatus
  ): Promise<VaultParsedObservationReviewItem>;

  listConfirmedObservations(
    filters?: VaultConfirmedObservationFilters
  ): Promise<VaultObservation[]>;
  saveConfirmedObservation(
    observation: VaultObservation
  ): Promise<VaultObservation>;
  updateConfirmedObservation(
    observationId: string,
    updates: VaultConfirmedObservationUpdate
  ): Promise<VaultObservation>;
  deleteConfirmedObservation(observationId: string): Promise<void>;
  listTrendPoints(
    testId: string,
    filters?: VaultTrendFilters
  ): Promise<VaultTrendPoint[]>;
  listAvailableTrendTests(filters?: VaultTrendFilters): Promise<VaultTrendTest[]>;
  getTrendForTest(testId: string, filters?: VaultTrendFilters): Promise<VaultTrend>;

  listAuditEvents(filters?: VaultAuditEventFilters): Promise<VaultAuditEvent[]>;
  recordAuditEvent(event: VaultAuditEvent): Promise<VaultAuditEvent>;

  exportVault(): Promise<SerializedPatientVault>;
  importVault(serializedVault: SerializedPatientVault): Promise<void>;
};

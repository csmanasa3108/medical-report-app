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
  saveParsedObservation(
    item: VaultParsedObservationReviewItem
  ): Promise<VaultParsedObservationReviewItem>;
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
  listTrendPoints(testId: string): Promise<VaultTrendPoint[]>;

  listAuditEvents(filters?: VaultAuditEventFilters): Promise<VaultAuditEvent[]>;
  recordAuditEvent(event: VaultAuditEvent): Promise<VaultAuditEvent>;

  exportVault(): Promise<SerializedPatientVault>;
  importVault(serializedVault: SerializedPatientVault): Promise<void>;
};

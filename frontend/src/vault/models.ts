export type PatientVaultMode = "api" | "local";

export type VaultResourceStatus =
  | "ACTIVE"
  | "INACTIVE"
  | "NEEDS_REVIEW"
  | "CONFIRMED"
  | "REJECTED"
  | "UPLOADED"
  | "PARSED"
  | "DELETED"
  | string;

export type VaultSourceType = "REPORT" | "MANUAL" | string;

export type PatientVaultManifest = {
  resourceType: "PatientVaultManifest";
  vaultId: string;
  patientUserId: string | null;
  schemaVersion: string;
  createdAt: string;
  updatedAt: string;
  encrypted: boolean;
  storageProvider: "localStorage" | "api" | string;
  documents: Array<{
    documentReferenceId: string;
    diagnosticReportId: string | null;
    encryptedBlobRef: string | null;
    contentType: string | null;
    sha256: string | null;
  }>;
  counts: {
    documentReferences: number;
    diagnosticReports: number;
    observations: number;
    reviewItems: number;
    auditEvents: number;
  };
};

export type VaultReportDocument = {
  resourceType: "DocumentReference";
  reportId: string;
  patientUserId: string | null;
  documentReferenceId?: string;
  diagnosticReportId?: string | null;
  originalFilename: string;
  contentType?: string | null;
  encryptedBlobRef?: string | null;
  sha256?: string | null;
  labName: string | null;
  reportDate: string | null;
  uploadedAt: string | null;
  status: VaultResourceStatus;
  sourceType?: "UPLOAD" | "IMPORT" | string;
};

export type VaultReportUploadMetadata = {
  reportDate?: string | null;
  labName?: string | null;
};

export type VaultDiagnosticReport = {
  resourceType: "DiagnosticReport";
  diagnosticReportId: string;
  reportId: string;
  documentReferenceId: string | null;
  status: VaultResourceStatus;
  patientUserId: string | null;
  labName: string | null;
  reportDate: string | null;
  issuedAt: string | null;
  originalFilename: string | null;
  observationIds: string[];
  parsedObservationIds: string[];
};

export type VaultObservation = {
  resourceType: "Observation";
  observationId: string;
  patientUserId: string | null;
  testId: string | null;
  testName: string;
  observedAt: string | null;
  valueText: string | null;
  numericValue: number | null;
  unit: string | null;
  referenceRange: string | null;
  abnormalFlag: string | null;
  status: "CONFIRMED" | VaultResourceStatus;
  sourceType: VaultSourceType;
  reportId: string | null;
  reportOriginalFilename: string | null;
  labName: string | null;
  reportDate: string | null;
  parsedObservationId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type VaultConfirmedObservationUpdate = Partial<
  Pick<
    VaultObservation,
    | "testId"
    | "testName"
    | "observedAt"
    | "valueText"
    | "numericValue"
    | "unit"
    | "referenceRange"
    | "abnormalFlag"
  >
>;

export type VaultParsedObservationReviewItem = {
  resourceType: "ParsedObservationReviewItem";
  parsedObservationId: string;
  patientUserId: string | null;
  reportId: string | null;
  reportOriginalFilename: string | null;
  labName: string | null;
  reportDate: string | null;
  testId: string | null;
  testName: string | null;
  observedAt: string | null;
  valueText: string | null;
  numericValue: number | null;
  unit: string | null;
  referenceRange: string | null;
  abnormalFlag: string | null;
  status: "NEEDS_REVIEW" | "CONFIRMED" | "REJECTED" | VaultResourceStatus;
  confirmedObservationId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type VaultParsedObservationUpdate = {
  rawTestName?: string | null;
  matchedTestId?: string | null;
  observedAt?: string | null;
  rawValue?: string | null;
  numericValue?: number | null;
  unit?: string | null;
  referenceRange?: string | null;
};

export type VaultAuditEvent = {
  resourceType: "AuditEvent";
  auditEventId: string;
  actorUserId: string | null;
  actorRole: string | null;
  patientUserId: string | null;
  action: string;
  resourceTypeName: string | null;
  resourceId: string | null;
  details: Record<string, unknown> | string | null;
  createdAt: string;
};

export type VaultTrendPoint = {
  trendPointId: string;
  observationId: string | null;
  parsedObservationId: string | null;
  testId: string;
  testName: string;
  observedAt: string | null;
  valueText: string | null;
  numericValue: number | null;
  unit: string | null;
  sourceType: VaultSourceType;
  reportId: string | null;
  reportOriginalFilename: string | null;
  labName: string | null;
  reportDate: string | null;
  referenceRange?: string | null;
  abnormalFlag?: string | null;
};

export type VaultTrendTest = {
  testId: string;
  canonicalName: string | null;
  displayName: string;
  name?: string | null;
  defaultUnit: string | null;
  category: string | null;
  pointCount: number;
  unit: string | null;
};

export type VaultTrend = {
  testId: string;
  testName: string | null;
  unit: string | null;
  points: VaultTrendPoint[];
  latestValue: number | null;
  previousValue: number | null;
  absoluteChange: number | null;
  percentChange: number | null;
};

export type VaultReportFilters = {
  patientId?: string | null;
};

export type VaultParsedObservationFilters = {
  patientId?: string | null;
  reportId?: string | null;
  status?: VaultParsedObservationReviewItem["status"] | null;
};

export type VaultConfirmedObservationFilters = {
  patientId?: string | null;
  testId?: string | null;
  startDate?: string | null;
  endDate?: string | null;
};

export type VaultTrendFilters = {
  patientId?: string | null;
  startDate?: string | null;
  endDate?: string | null;
};

export type VaultAuditEventFilters = {
  patientId?: string | null;
  action?: string | null;
  resourceType?: string | null;
  limit?: number | null;
};

export type SerializedPatientVault = string;

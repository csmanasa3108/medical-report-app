import { ApiBackedPatientVaultService } from "./ApiBackedPatientVaultService";
import { LocalPatientVaultService } from "./LocalPatientVaultService";
import type { PatientVaultService } from "./PatientVaultService";
import { getPatientVaultMode } from "./config";
import type { PatientVaultMode } from "./models";

export type { PatientVaultService } from "./PatientVaultService";
export type {
  PatientVaultManifest,
  PatientVaultMode,
  SerializedPatientVault,
  VaultAuditEvent,
  VaultAuditEventFilters,
  VaultConfirmedObservationFilters,
  VaultDiagnosticReport,
  VaultObservation,
  VaultParsedObservationFilters,
  VaultParsedObservationReviewItem,
  VaultParsedObservationUpdate,
  VaultReportDocument,
  VaultReportFilters,
  VaultReportUploadMetadata,
  VaultResourceStatus,
  VaultSourceType,
  VaultTrend,
  VaultTrendFilters,
  VaultTrendTest,
  VaultTrendPoint
} from "./models";
export { ApiBackedPatientVaultService } from "./ApiBackedPatientVaultService";
export {
  LocalPatientVaultService,
  clearLocalPatientVault,
  hasLocalPatientVault,
  isLocalPatientVaultUnlocked,
  lockLocalPatientVault,
  unlockLocalPatientVault
} from "./LocalPatientVaultService";
export { getPatientVaultMode } from "./config";

let patientVaultService: PatientVaultService | null = null;

export function createPatientVaultService(
  mode: PatientVaultMode = getPatientVaultMode()
): PatientVaultService {
  if (mode === "local") {
    return new LocalPatientVaultService();
  }

  return new ApiBackedPatientVaultService();
}

export function getPatientVaultService(): PatientVaultService {
  patientVaultService ??= createPatientVaultService();
  return patientVaultService;
}

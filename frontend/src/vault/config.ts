import type { PatientVaultMode } from "./models";

const DEFAULT_PATIENT_VAULT_MODE: PatientVaultMode = "api";

export function getPatientVaultMode(): PatientVaultMode {
  const configuredMode = import.meta.env.VITE_PATIENT_VAULT_MODE;

  if (configuredMode === "local" || configuredMode === "api") {
    return configuredMode;
  }

  return DEFAULT_PATIENT_VAULT_MODE;
}

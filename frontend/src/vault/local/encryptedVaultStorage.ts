import type { EncryptedVaultBlob } from "../crypto/vaultCrypto";

const LOCAL_ENCRYPTED_VAULT_STORAGE_KEY =
  "soverahealth.patientVault.encrypted.local";

function assertBrowserStorageAvailable() {
  if (typeof window === "undefined" || !window.localStorage) {
    throw new Error("Local encrypted vault requires browser localStorage.");
  }
}

function parseEncryptedVaultBlob(value: string): EncryptedVaultBlob {
  const parsed = JSON.parse(value) as Partial<EncryptedVaultBlob>;

  if (
    parsed.version !== "local-vault-aes-gcm-v1" ||
    !parsed.kdf ||
    !parsed.cipher ||
    typeof parsed.ciphertext !== "string"
  ) {
    throw new Error("Encrypted vault backup is not a supported vault blob.");
  }

  return parsed as EncryptedVaultBlob;
}

export function hasEncryptedVaultBlob() {
  assertBrowserStorageAvailable();
  return window.localStorage.getItem(LOCAL_ENCRYPTED_VAULT_STORAGE_KEY) !== null;
}

export function loadEncryptedVaultBlob() {
  assertBrowserStorageAvailable();
  const serializedBlob = window.localStorage.getItem(
    LOCAL_ENCRYPTED_VAULT_STORAGE_KEY
  );

  return serializedBlob ? parseEncryptedVaultBlob(serializedBlob) : null;
}

export function saveEncryptedVaultBlob(blob: EncryptedVaultBlob) {
  assertBrowserStorageAvailable();
  window.localStorage.setItem(
    LOCAL_ENCRYPTED_VAULT_STORAGE_KEY,
    JSON.stringify(blob)
  );
}

export function clearEncryptedVault() {
  assertBrowserStorageAvailable();
  window.localStorage.removeItem(LOCAL_ENCRYPTED_VAULT_STORAGE_KEY);
}

export function parseSerializedEncryptedVault(
  serializedVault: string
): EncryptedVaultBlob {
  return parseEncryptedVaultBlob(serializedVault);
}


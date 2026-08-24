import { ChangeEvent, useRef, useState } from "react";
import { getPatientVaultService } from "../vault";
import { getPatientVaultMode } from "../vault/config";
import { clearLocalPatientVault } from "../vault/LocalPatientVaultService";

const BACKUP_FILENAME = "soverahealth-vault-backup.json";

function validateEncryptedVaultBackup(serializedVault: string) {
  const parsed = JSON.parse(serializedVault) as {
    version?: unknown;
    kdf?: unknown;
    cipher?: unknown;
    ciphertext?: unknown;
  };

  if (
    parsed.version !== "local-vault-aes-gcm-v1" ||
    typeof parsed.kdf !== "object" ||
    parsed.kdf === null ||
    typeof parsed.cipher !== "object" ||
    parsed.cipher === null ||
    typeof parsed.ciphertext !== "string"
  ) {
    throw new Error("Select a valid encrypted SoveraHealth vault backup file.");
  }
}

function downloadTextFile(filename: string, contents: string) {
  const blob = new Blob([contents], {
    type: "application/json"
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function VaultSettingsPage() {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [successMessage, setSuccessMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [isExporting, setIsExporting] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [isClearing, setIsClearing] = useState(false);
  const isLocalMode = getPatientVaultMode() === "local";

  async function handleExport() {
    setSuccessMessage("");
    setErrorMessage("");
    setIsExporting(true);

    try {
      const serializedVault = await getPatientVaultService().exportVault();
      validateEncryptedVaultBackup(serializedVault);
      downloadTextFile(BACKUP_FILENAME, serializedVault);
      setSuccessMessage("Encrypted vault backup exported.");
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to export encrypted vault backup."
      );
    } finally {
      setIsExporting(false);
    }
  }

  async function handleImport(event: ChangeEvent<HTMLInputElement>) {
    const selectedFile = event.target.files?.item(0);

    setSuccessMessage("");
    setErrorMessage("");

    if (!selectedFile) {
      return;
    }

    setIsImporting(true);

    try {
      const serializedVault = await selectedFile.text();
      validateEncryptedVaultBackup(serializedVault);

      const confirmed = window.confirm(
        "Importing will replace the current local vault on this device."
      );

      if (!confirmed) {
        return;
      }

      await getPatientVaultService().importVault(serializedVault);
      setSuccessMessage(
        "Encrypted vault backup imported. Reloading so you can unlock it."
      );
      window.setTimeout(() => window.location.reload(), 900);
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to import encrypted vault backup."
      );
    } finally {
      setIsImporting(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  }

  function handleClear() {
    setSuccessMessage("");
    setErrorMessage("");

    const confirmed = window.confirm(
      "Clear the local vault from this device? This cannot be undone without an encrypted backup file."
    );

    if (!confirmed) {
      return;
    }

    setIsClearing(true);
    clearLocalPatientVault();
    setSuccessMessage("Local encrypted vault cleared. Reloading.");
    window.setTimeout(() => window.location.reload(), 700);
  }

  if (!isLocalMode) {
    return (
      <section className="page-section">
        <p className="eyebrow">Vault</p>
        <h2 className="page-title">Vault Settings</h2>
        <p className="page-description">
          Vault settings are available only in local vault mode.
        </p>
      </section>
    );
  }

  return (
    <section className="vault-settings-page">
      <div className="section-header">
        <div>
          <p className="eyebrow">Local encrypted vault</p>
          <h2 className="page-title">Vault Settings</h2>
          <p className="page-description">
            Export, import, or clear the encrypted vault stored on this device.
          </p>
        </div>
      </div>

      <div className="vault-warning-panel">
        <strong>Prototype only</strong>
        <p>
          Do not store real medical data yet. Lost passphrases cannot be
          recovered, and lost browser storage can mean data loss unless you keep
          an encrypted backup.
        </p>
      </div>

      {successMessage ? (
        <p className="status-message success-message">{successMessage}</p>
      ) : null}

      {errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}

      <div className="vault-settings-grid">
        <section className="vault-settings-card">
          <div>
            <h3>Export encrypted vault</h3>
            <p>
              Download the encrypted backup blob for this device vault. Keep
              this file safe. Anyone with this file and your passphrase may
              access your vault.
            </p>
          </div>
          <button
            className="action-button"
            type="button"
            disabled={isExporting}
            onClick={handleExport}
          >
            {isExporting ? "Exporting..." : "Export encrypted vault"}
          </button>
        </section>

        <section className="vault-settings-card">
          <div>
            <h3>Import encrypted vault</h3>
            <p>
              Replace this device vault with an exported encrypted backup. You
              will need to unlock it with the backup passphrase after import.
            </p>
          </div>
          <label className="button-link secondary vault-import-button">
            {isImporting ? "Importing..." : "Import encrypted vault"}
            <input
              ref={fileInputRef}
              type="file"
              accept="application/json,.json"
              disabled={isImporting}
              onChange={handleImport}
            />
          </label>
        </section>

        <section className="vault-settings-card vault-danger-card">
          <div>
            <h3>Clear local vault from this device</h3>
            <p>
              Remove the encrypted vault blob from this browser. This cannot be
              undone without a backup file.
            </p>
          </div>
          <button
            className="action-button secondary danger"
            type="button"
            disabled={isClearing}
            onClick={handleClear}
          >
            {isClearing ? "Clearing..." : "Clear local vault from this device"}
          </button>
        </section>
      </div>
    </section>
  );
}

export default VaultSettingsPage;


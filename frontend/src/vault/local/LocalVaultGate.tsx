import { FormEvent, ReactNode, useEffect, useState } from "react";
import { getPatientVaultMode } from "../config";
import {
  hasLocalPatientVault,
  isLocalPatientVaultUnlocked,
  unlockLocalPatientVault
} from "../LocalPatientVaultService";

type LocalVaultGateProps = {
  children: ReactNode;
};

function LocalVaultGate({ children }: LocalVaultGateProps) {
  const isLocalMode = getPatientVaultMode() === "local";
  const [isUnlocked, setIsUnlocked] = useState(
    !isLocalMode || isLocalPatientVaultUnlocked()
  );
  const [hasExistingVault, setHasExistingVault] = useState(false);
  const [passphrase, setPassphrase] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!isLocalMode) {
      return;
    }

    try {
      setHasExistingVault(hasLocalPatientVault());
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to inspect local vault storage."
      );
    }
  }, [isLocalMode]);

  async function handleUnlock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");
    setIsSubmitting(true);

    try {
      await unlockLocalPatientVault(passphrase);
      setPassphrase("");
      setIsUnlocked(true);
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to unlock the local encrypted vault."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!isLocalMode) {
    return <>{children}</>;
  }

  if (!isUnlocked) {
    return (
      <main className="local-vault-unlock-page">
        <section className="local-vault-unlock-card">
          <p className="eyebrow">Development vault mode</p>
          <h1>Unlock patient vault</h1>
          <p className="page-description">
            Local encrypted vault mode is a prototype. Do not store real medical
            data.
          </p>

          <form className="metadata-form local-vault-unlock-form" onSubmit={handleUnlock}>
            <label className="full-span-field">
              Passphrase
              <input
                autoComplete="current-password"
                type="password"
                value={passphrase}
                onChange={(event) => setPassphrase(event.target.value)}
                required
              />
            </label>
            <button type="submit" disabled={isSubmitting}>
              {hasExistingVault ? "Unlock Vault" : "Create Encrypted Vault"}
            </button>
          </form>

          {errorMessage ? (
            <p className="status-message error-message" role="alert">
              {errorMessage}
            </p>
          ) : null}

          <p className="local-vault-helper">
            The passphrase is not stored. If it is lost, this local vault cannot
            be recovered.
          </p>
        </section>
      </main>
    );
  }

  return (
    <>
      <div className="local-vault-warning" role="status">
        Local encrypted vault mode is a prototype. Do not store real medical data.
      </div>
      {children}
    </>
  );
}

export default LocalVaultGate;


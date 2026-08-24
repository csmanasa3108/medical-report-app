const ENCRYPTED_VAULT_VERSION = "local-vault-aes-gcm-v1";
const PBKDF2_ITERATIONS = 250000;
const SALT_BYTE_LENGTH = 16;
const IV_BYTE_LENGTH = 12;

export type EncryptedVaultBlob = {
  version: typeof ENCRYPTED_VAULT_VERSION;
  kdf: {
    name: "PBKDF2";
    hash: "SHA-256";
    iterations: number;
    salt: string;
  };
  cipher: {
    name: "AES-GCM";
    iv: string;
  };
  ciphertext: string;
  createdAt: string;
  updatedAt: string;
};

function assertWebCryptoAvailable() {
  if (typeof crypto === "undefined" || !crypto.subtle) {
    throw new Error("Local encrypted vault mode requires browser Web Crypto.");
  }
}

function bytesToBase64(bytes: Uint8Array) {
  let binary = "";

  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary);
}

function base64ToBytes(value: string) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  return bytes;
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const buffer = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(buffer).set(bytes);
  return buffer;
}

function getRandomBytes(length: number) {
  assertWebCryptoAvailable();
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}

export async function deriveKey(passphrase: string, salt: Uint8Array) {
  assertWebCryptoAvailable();

  const passphraseKey = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(passphrase),
    "PBKDF2",
    false,
    ["deriveKey"]
  );

  return crypto.subtle.deriveKey(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: toArrayBuffer(salt),
      iterations: PBKDF2_ITERATIONS
    },
    passphraseKey,
    {
      name: "AES-GCM",
      length: 256
    },
    false,
    ["encrypt", "decrypt"]
  );
}

export function createVaultSalt() {
  return bytesToBase64(getRandomBytes(SALT_BYTE_LENGTH));
}

export async function deriveKeyFromStoredSalt(
  passphrase: string,
  salt: string
) {
  return deriveKey(passphrase, base64ToBytes(salt));
}

export async function encryptJsonWithKey<T>(
  data: T,
  key: CryptoKey,
  salt: string,
  previousBlob?: EncryptedVaultBlob | null
): Promise<EncryptedVaultBlob> {
  const iv = getRandomBytes(IV_BYTE_LENGTH);
  const encodedPayload = new TextEncoder().encode(JSON.stringify(data));
  const encryptedPayload = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv: toArrayBuffer(iv)
    },
    key,
    encodedPayload
  );
  const timestamp = new Date().toISOString();

  return {
    version: ENCRYPTED_VAULT_VERSION,
    kdf: {
      name: "PBKDF2",
      hash: "SHA-256",
      iterations: PBKDF2_ITERATIONS,
      salt
    },
    cipher: {
      name: "AES-GCM",
      iv: bytesToBase64(iv)
    },
    ciphertext: bytesToBase64(new Uint8Array(encryptedPayload)),
    createdAt: previousBlob?.createdAt ?? timestamp,
    updatedAt: timestamp
  };
}

export async function encryptJson<T>(
  data: T,
  passphrase: string,
  previousBlob?: EncryptedVaultBlob | null
): Promise<EncryptedVaultBlob> {
  const salt = createVaultSalt();
  const key = await deriveKeyFromStoredSalt(passphrase, salt);
  return encryptJsonWithKey(data, key, salt, previousBlob);
}

export async function decryptJsonWithKey<T>(
  encryptedBlob: EncryptedVaultBlob,
  key: CryptoKey
): Promise<T> {
  assertWebCryptoAvailable();

  if (encryptedBlob.version !== ENCRYPTED_VAULT_VERSION) {
    throw new Error("Encrypted vault version is not supported.");
  }

  const decryptedPayload = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: toArrayBuffer(base64ToBytes(encryptedBlob.cipher.iv))
    },
    key,
    base64ToBytes(encryptedBlob.ciphertext)
  );

  return JSON.parse(new TextDecoder().decode(decryptedPayload)) as T;
}

export async function decryptJson<T>(
  encryptedBlob: EncryptedVaultBlob,
  passphrase: string
): Promise<T> {
  const key = await deriveKeyFromStoredSalt(passphrase, encryptedBlob.kdf.salt);
  return decryptJsonWithKey(encryptedBlob, key);
}

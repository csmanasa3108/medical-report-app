export const API_BASE_URL = "http://localhost:8080";

export type DevUserKey = "patient" | "clinician";

export type DevUser = {
  key: DevUserKey;
  label: string;
  role: "PATIENT" | "CLINICIAN";
  userId: string;
};

const DEV_USER_STORAGE_KEY = "soverahealth.devUser";

export const DEV_USERS: DevUser[] = [
  {
    key: "patient",
    label: "Demo Patient",
    role: "PATIENT",
    userId: "00000000-0000-0000-0000-000000000101"
  },
  {
    key: "clinician",
    label: "Demo Clinician",
    role: "CLINICIAN",
    userId: "00000000-0000-0000-0000-000000000102"
  }
];

const DEFAULT_DEV_USER = DEV_USERS[0];
const DEMO_PATIENT_USER_ID = "00000000-0000-0000-0000-000000000101";

export function getCurrentDevUser(): DevUser {
  if (typeof window === "undefined") {
    return DEFAULT_DEV_USER;
  }

  const storedKey = window.localStorage.getItem(DEV_USER_STORAGE_KEY);
  return DEV_USERS.find((user) => user.key === storedKey) ?? DEFAULT_DEV_USER;
}

export function setCurrentDevUser(key: DevUserKey): DevUser {
  const selectedUser =
    DEV_USERS.find((user) => user.key === key) ?? DEFAULT_DEV_USER;

  if (typeof window !== "undefined") {
    window.localStorage.setItem(DEV_USER_STORAGE_KEY, selectedUser.key);
  }

  return selectedUser;
}

function buildJsonHeaders(headers?: HeadersInit): Headers {
  const requestHeaders = new Headers(headers);
  requestHeaders.set("Content-Type", "application/json");
  requestHeaders.set("X-User-Id", getCurrentDevUser().userId);
  return requestHeaders;
}

function buildDevUserHeaders(headers?: HeadersInit): Headers {
  const requestHeaders = new Headers(headers);
  requestHeaders.set("X-User-Id", getCurrentDevUser().userId);
  return requestHeaders;
}

function appendQueryParam(path: string, key: string, value: string): string {
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
}

function withDevPatientScope(path: string): string {
  if (getCurrentDevUser().role !== "CLINICIAN") {
    return path;
  }

  return appendQueryParam(path, "patientId", DEMO_PATIENT_USER_ID);
}

export type TestCatalogResponse = {
  id: string;
  canonicalName: string;
  displayName: string;
  name?: string | null;
  defaultUnit: string | null;
  category: string | null;
};

export type CreateLabObservationRequest = {
  testId: string;
  observedAt: string;
  numericValue: number;
  unit: string;
  referenceLow: number;
  referenceHigh: number;
  abnormalFlag: string;
};

export type LabObservationResponse = {
  id: string;
  testId: string;
  testName: string;
  observedAt: string;
  numericValue: number;
  unit: string;
  referenceLow: number;
  referenceHigh: number;
  abnormalFlag: string;
};

export type LabTrendPointResponse = {
  observedAt?: string | null;
  numericValue?: number | string | null;
  unit?: string | null;
  sourceType?: "REPORT" | "MANUAL" | string | null;
  reportId?: string | null;
  reportOriginalFilename?: string | null;
  labName?: string | null;
  reportDate?: string | null;
  parsedObservationId?: string | null;
  date?: string;
  value?: number | string;
};

export type LabTrendResponse = {
  testId: string;
  testName?: string | null;
  unit: string | null;
  points: LabTrendPointResponse[];
  latestValue?: number | null;
  previousValue?: number | null;
  absoluteChange?: number | null;
  percentChange?: number | null;
};

export type CreateReportRequest = {
  originalFilename: string;
  reportDate: string;
  labName: string;
};

export type ReportResponse = {
  id: string;
  originalFilename: string;
  reportDate: string | null;
  labName: string | null;
  status: string;
  createdAt: string;
};

export type ParsedObservationResponse = {
  id?: string;
  rawTestName: string | null;
  matchedTestId: string | null;
  observedAt: string | null;
  rawValue: string | null;
  numericValue: number | null;
  unit: string | null;
  referenceRange: string | null;
  status: string | null;
};

export type UpdateParsedObservationRequest = {
  rawTestName: string | null;
  matchedTestId: string | null;
  observedAt: string | null;
  rawValue: string | null;
  numericValue: number | null;
  unit: string | null;
  referenceRange: string | null;
};

export type UploadReportRequest = {
  file: File;
  reportDate?: string;
  labName?: string;
};

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const { headers, ...requestOptions } = options;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    headers: buildJsonHeaders(headers)
  });

  if (!response.ok) {
    const fallbackMessage = `Request failed with status ${response.status}`;
    const responseText = await response.text();
    let message = fallbackMessage;

    if (responseText) {
      try {
        const body = JSON.parse(responseText) as { message?: string; error?: string };
        message = body.message || body.error || fallbackMessage;
      } catch {
        message = responseText;
      }
    }

    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

async function apiCommand(path: string, options: RequestInit = {}) {
  const { headers, ...requestOptions } = options;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    headers: buildJsonHeaders(headers)
  });

  if (!response.ok) {
    const fallbackMessage = `Request failed with status ${response.status}`;
    const responseText = await response.text();
    let message = fallbackMessage;

    if (responseText) {
      try {
        const body = JSON.parse(responseText) as { message?: string; error?: string };
        message = body.message || body.error || fallbackMessage;
      } catch {
        message = responseText;
      }
    }

    throw new Error(message);
  }
}

export function getTests() {
  return apiRequest<TestCatalogResponse[]>("/api/tests");
}

export function createObservation(payload: CreateLabObservationRequest) {
  return apiRequest<LabObservationResponse>("/api/observations", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function getLabTrend(testId: string) {
  return apiRequest<LabTrendResponse>(
    withDevPatientScope(
      `/api/analytics/tests/${encodeURIComponent(testId)}/trend`
    )
  );
}

export function getReports() {
  return apiRequest<ReportResponse[]>(withDevPatientScope("/api/reports"));
}

export function getReport(reportId: string) {
  return apiRequest<ReportResponse>(
    `/api/reports/${encodeURIComponent(reportId)}`
  );
}

export function deleteReport(reportId: string) {
  return apiCommand(`/api/reports/${encodeURIComponent(reportId)}`, {
    method: "DELETE"
  });
}

export function extractReportText(reportId: string) {
  return apiCommand(
    `/api/reports/${encodeURIComponent(reportId)}/extract-text`,
    {
      method: "POST"
    }
  );
}

export function parseReportObservations(reportId: string) {
  return apiCommand(
    `/api/reports/${encodeURIComponent(reportId)}/parse-observations`,
    {
      method: "POST"
    }
  );
}

export function getParsedObservations(reportId: string) {
  return apiRequest<ParsedObservationResponse[]>(
    `/api/reports/${encodeURIComponent(reportId)}/parsed-observations`
  );
}

export function confirmParsedObservation(parsedObservationId: string) {
  return apiCommand(
    `/api/parsed-observations/${encodeURIComponent(parsedObservationId)}/confirm`,
    {
      method: "POST"
    }
  );
}

export function updateParsedObservation(
  parsedObservationId: string,
  payload: UpdateParsedObservationRequest
) {
  return apiCommand(
    `/api/parsed-observations/${encodeURIComponent(parsedObservationId)}`,
    {
      method: "PATCH",
      body: JSON.stringify(payload)
    }
  );
}

export function createReport(payload: CreateReportRequest) {
  return apiRequest<ReportResponse>("/api/reports", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function uploadReport(payload: UploadReportRequest) {
  const formData = new FormData();
  formData.append("file", payload.file);
  formData.append("reportDate", payload.reportDate ?? "");
  formData.append("labName", payload.labName ?? "");

  const response = await fetch(`${API_BASE_URL}/api/reports/upload`, {
    method: "POST",
    headers: buildDevUserHeaders(),
    body: formData
  });

  if (!response.ok) {
    const fallbackMessage = `Upload failed with status ${response.status}`;
    const responseText = await response.text();
    let message = fallbackMessage;

    if (responseText) {
      try {
        const body = JSON.parse(responseText) as { message?: string; error?: string };
        message = body.message || body.error || fallbackMessage;
      } catch {
        message = responseText;
      }
    }

    throw new Error(message);
  }

  return response.json() as Promise<ReportResponse>;
}

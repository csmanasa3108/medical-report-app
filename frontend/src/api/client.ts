export const API_BASE_URL = "http://localhost:8080";

export type TestCatalogResponse = {
  id: string;
  canonicalName: string;
  displayName: string;
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
  date: string;
  value: number | string;
};

export type LabTrendResponse = {
  testId: string;
  testName: string;
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

export type UploadReportRequest = {
  file: File;
  reportDate?: string;
  labName?: string;
};

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers
    },
    ...options
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
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers
    },
    ...options
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
    `/api/analytics/tests/${encodeURIComponent(testId)}/trend`
  );
}

export function getReports() {
  return apiRequest<ReportResponse[]>("/api/reports");
}

export function getReport(reportId: string) {
  return apiRequest<ReportResponse>(
    `/api/reports/${encodeURIComponent(reportId)}`
  );
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

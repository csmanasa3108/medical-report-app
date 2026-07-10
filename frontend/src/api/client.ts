export const API_BASE_URL = "http://localhost:8080";

export type TestCatalogResponse = {
  id: string;
  canonicalName: string;
  displayName: string;
  defaultUnit: string;
  category: string;
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
  unit: string;
  points: LabTrendPointResponse[];
  latestValue?: number | null;
  previousValue?: number | null;
  absoluteChange?: number | null;
  percentChange?: number | null;
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

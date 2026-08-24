import {
  confirmParsedObservation,
  createReport,
  deleteReport as deleteBackendReport,
  getAuditEvents,
  getLabTrend,
  getParsedObservationReviewQueue,
  getParsedObservations,
  getReport as getBackendReport,
  getReports,
  getTests,
  parseReportObservations,
  rejectParsedObservation,
  updateParsedObservation as updateBackendParsedObservation,
  uploadReport as uploadBackendReport
} from "../api/client";
import type {
  AuditEventResponse,
  LabObservationResponse,
  LabTrendPointResponse,
  ParsedObservationResponse,
  ParsedObservationReviewResponse,
  ReportResponse,
  TestCatalogResponse,
  UpdateParsedObservationRequest
} from "../api/client";
import type { PatientVaultService } from "./PatientVaultService";
import type {
  PatientVaultManifest,
  SerializedPatientVault,
  VaultAuditEvent,
  VaultAuditEventFilters,
  VaultConfirmedObservationFilters,
  VaultObservation,
  VaultParsedObservationFilters,
  VaultParsedObservationReviewItem,
  VaultParsedObservationUpdate,
  VaultReportDocument,
  VaultReportFilters,
  VaultReportUploadMetadata,
  VaultResourceStatus,
  VaultTrend,
  VaultTrendFilters,
  VaultTrendTest,
  VaultTrendPoint
} from "./models";

function unsupported(operation: string): Error {
  return new Error(
    `${operation} is not supported by the API-backed patient vault adapter yet.`
  );
}

function nowIso() {
  return new Date().toISOString();
}

function mapReportResponse(
  report: ReportResponse,
  patientUserId: string | null = null
): VaultReportDocument {
  return {
    resourceType: "DocumentReference",
    reportId: report.id,
    patientUserId,
    documentReferenceId: report.id,
    diagnosticReportId: null,
    originalFilename: report.originalFilename,
    contentType: null,
    encryptedBlobRef: null,
    sha256: null,
    labName: report.labName,
    reportDate: report.reportDate,
    uploadedAt: report.createdAt,
    status: report.status,
    sourceType: "UPLOAD"
  };
}

function mapReviewResponse(
  item: ParsedObservationReviewResponse
): VaultParsedObservationReviewItem {
  return {
    resourceType: "ParsedObservationReviewItem",
    parsedObservationId: item.parsedObservationId,
    patientUserId: null,
    reportId: item.reportId,
    reportOriginalFilename: item.reportOriginalFilename,
    labName: item.labName,
    reportDate: item.reportDate,
    testId: item.testId,
    testName: item.testName,
    observedAt: item.observedAt,
    valueText: item.valueText,
    numericValue: item.numericValue,
    unit: item.unit,
    referenceRange: item.referenceRange,
    abnormalFlag: item.abnormalFlag,
    status: item.status,
    confirmedObservationId: null,
    createdAt: item.createdAt,
    updatedAt: item.createdAt
  };
}

function mapReportParsedObservation(
  item: ParsedObservationResponse,
  reportId: string
): VaultParsedObservationReviewItem {
  const timestamp = nowIso();

  return {
    resourceType: "ParsedObservationReviewItem",
    parsedObservationId: item.id ?? `${reportId}:${item.rawTestName ?? "item"}`,
    patientUserId: null,
    reportId,
    reportOriginalFilename: null,
    labName: null,
    reportDate: null,
    testId: item.matchedTestId,
    testName: item.rawTestName,
    observedAt: item.observedAt,
    valueText: item.rawValue,
    numericValue: item.numericValue,
    unit: item.unit,
    referenceRange: item.referenceRange,
    abnormalFlag: null,
    status: item.status ?? "NEEDS_REVIEW",
    confirmedObservationId: null,
    createdAt: timestamp,
    updatedAt: timestamp
  };
}

function mapConfirmedObservationToReviewItem(
  observation: LabObservationResponse,
  parsedObservationId: string
): VaultParsedObservationReviewItem {
  const timestamp = nowIso();

  return {
    resourceType: "ParsedObservationReviewItem",
    parsedObservationId,
    patientUserId: null,
    reportId: null,
    reportOriginalFilename: null,
    labName: null,
    reportDate: null,
    testId: observation.testId,
    testName: observation.testName,
    observedAt: observation.observedAt,
    valueText: String(observation.numericValue),
    numericValue: observation.numericValue,
    unit: observation.unit,
    referenceRange: null,
    abnormalFlag: observation.abnormalFlag,
    status: "CONFIRMED",
    confirmedObservationId: observation.id,
    createdAt: timestamp,
    updatedAt: timestamp
  };
}

function mapLabObservationResponse(
  observation: LabObservationResponse
): VaultObservation {
  const timestamp = nowIso();

  return {
    resourceType: "Observation",
    observationId: observation.id,
    patientUserId: null,
    testId: observation.testId,
    testName: observation.testName,
    observedAt: observation.observedAt,
    valueText: String(observation.numericValue),
    numericValue: observation.numericValue,
    unit: observation.unit,
    referenceRange:
      observation.referenceLow !== null && observation.referenceHigh !== null
        ? `${observation.referenceLow} - ${observation.referenceHigh}`
        : null,
    abnormalFlag: observation.abnormalFlag,
    status: "CONFIRMED",
    sourceType: "REPORT",
    reportId: null,
    reportOriginalFilename: null,
    labName: null,
    reportDate: null,
    parsedObservationId: null,
    createdAt: timestamp,
    updatedAt: timestamp
  };
}

function mapParsedObservationUpdate(
  updates: VaultParsedObservationUpdate
): UpdateParsedObservationRequest {
  return {
    rawTestName: updates.rawTestName ?? null,
    matchedTestId: updates.matchedTestId ?? null,
    observedAt: updates.observedAt ?? null,
    rawValue: updates.rawValue ?? null,
    numericValue: updates.numericValue ?? null,
    unit: updates.unit ?? null,
    referenceRange: updates.referenceRange ?? null
  };
}

function mapTrendPoint(
  testId: string,
  testName: string,
  point: LabTrendPointResponse
): VaultTrendPoint {
  const numericValue =
    point.numericValue === null || point.numericValue === undefined
      ? null
      : Number(point.numericValue);
  const valueText =
    point.value !== undefined && point.value !== null ? String(point.value) : null;

  return {
    trendPointId:
      point.parsedObservationId ??
      `${testId}:${point.observedAt ?? point.date ?? "unknown"}`,
    observationId: null,
    parsedObservationId: point.parsedObservationId ?? null,
    testId,
    testName,
    observedAt: point.observedAt ?? point.date ?? null,
    valueText,
    numericValue: Number.isFinite(numericValue) ? numericValue : null,
    unit: point.unit ?? null,
    sourceType: point.sourceType ?? "REPORT",
    reportId: point.reportId ?? null,
    reportOriginalFilename: point.reportOriginalFilename ?? null,
    labName: point.labName ?? null,
    reportDate: point.reportDate ?? null
  };
}

function mapTrendResponse(
  trend: {
    testId: string;
    testName?: string | null;
    unit: string | null;
    points: LabTrendPointResponse[];
    latestValue?: number | null;
    previousValue?: number | null;
    absoluteChange?: number | null;
    percentChange?: number | null;
  },
  fallbackTestName: string
): VaultTrend {
  return {
    testId: trend.testId,
    testName: trend.testName ?? fallbackTestName,
    unit: trend.unit,
    points: trend.points.map((point) =>
      mapTrendPoint(trend.testId, trend.testName ?? fallbackTestName, point)
    ),
    latestValue: trend.latestValue ?? null,
    previousValue: trend.previousValue ?? null,
    absoluteChange: trend.absoluteChange ?? null,
    percentChange: trend.percentChange ?? null
  };
}

function hasUsableTrendPoint(point: VaultTrendPoint) {
  return Boolean(point.observedAt) && Number.isFinite(Number(point.numericValue));
}

function isInDateRange(
  observedAt: string | null,
  filters: VaultTrendFilters | VaultConfirmedObservationFilters
) {
  if (!observedAt) {
    return false;
  }

  if (filters.startDate && observedAt < filters.startDate) {
    return false;
  }

  if (filters.endDate && observedAt > filters.endDate) {
    return false;
  }

  return true;
}

function filterTrendPoints(points: VaultTrendPoint[], filters: VaultTrendFilters = {}) {
  return points.filter((point) => isInDateRange(point.observedAt, filters));
}

function mapAvailableTrendTest(
  test: TestCatalogResponse,
  trend: VaultTrend
): VaultTrendTest {
  return {
    testId: test.id,
    canonicalName: test.canonicalName,
    displayName: test.displayName || test.canonicalName,
    name: test.name ?? null,
    defaultUnit: test.defaultUnit,
    category: test.category,
    pointCount: trend.points.filter(hasUsableTrendPoint).length,
    unit: trend.unit ?? test.defaultUnit
  };
}

function mapAuditEvent(event: AuditEventResponse): VaultAuditEvent {
  return {
    resourceType: "AuditEvent",
    auditEventId: event.id,
    actorUserId: event.actorUserId,
    actorRole: event.actorRole,
    patientUserId: event.patientUserId,
    action: event.action,
    resourceTypeName: event.resourceType,
    resourceId: event.resourceId,
    details: event.details,
    createdAt: event.createdAt
  };
}

export class ApiBackedPatientVaultService implements PatientVaultService {
  async getManifest(): Promise<PatientVaultManifest> {
    const timestamp = nowIso();

    return {
      resourceType: "PatientVaultManifest",
      vaultId: "api-control-plane",
      patientUserId: null,
      schemaVersion: "api-adapter-v1",
      createdAt: timestamp,
      updatedAt: timestamp,
      encrypted: false,
      storageProvider: "api",
      documents: [],
      counts: {
        documentReferences: 0,
        diagnosticReports: 0,
        observations: 0,
        reviewItems: 0,
        auditEvents: 0
      }
    };
  }

  async listReports(filters: VaultReportFilters = {}) {
    const reports = await getReports(filters.patientId);
    return reports.map((report) =>
      mapReportResponse(report, filters.patientId ?? null)
    );
  }

  async getReport(reportId: string) {
    const report = await getBackendReport(reportId);
    return mapReportResponse(report);
  }

  async saveReport(report: VaultReportDocument) {
    const savedReport = await createReport({
      originalFilename: report.originalFilename,
      reportDate: report.reportDate ?? "",
      labName: report.labName ?? ""
    });

    return mapReportResponse(savedReport, report.patientUserId);
  }

  async uploadReport(file: File, metadata: VaultReportUploadMetadata = {}) {
    const uploadedReport = await uploadBackendReport({
      file,
      reportDate: metadata.reportDate ?? "",
      labName: metadata.labName ?? ""
    });

    return mapReportResponse(uploadedReport);
  }

  async deleteReport(reportId: string) {
    await deleteBackendReport(reportId);
  }

  async listParsedObservations(filters: VaultParsedObservationFilters = {}) {
    if (filters.reportId) {
      return this.getParsedObservationsForReport(filters.reportId).then(
        (observations) =>
          observations.filter(
            (item) => !filters.status || item.status === filters.status
          )
      );
    }

    const reviewItems = await getParsedObservationReviewQueue(
      filters.patientId,
      filters.status
    );
    return reviewItems.map(mapReviewResponse);
  }

  async getParsedObservationsForReport(reportId: string) {
    const observations = await getParsedObservations(reportId);
    return observations.map((item) => mapReportParsedObservation(item, reportId));
  }

  async refreshParsedObservations(reportId: string) {
    await parseReportObservations(reportId);
    return this.getParsedObservationsForReport(reportId);
  }

  async saveParsedObservation(
    _item: VaultParsedObservationReviewItem
  ): Promise<VaultParsedObservationReviewItem> {
    throw unsupported("saveParsedObservation");
  }

  async updateParsedObservation(
    id: string,
    updates: VaultParsedObservationUpdate
  ) {
    await updateBackendParsedObservation(id, mapParsedObservationUpdate(updates));

    return {
      resourceType: "ParsedObservationReviewItem" as const,
      parsedObservationId: id,
      patientUserId: null,
      reportId: null,
      reportOriginalFilename: null,
      labName: null,
      reportDate: null,
      testId: updates.matchedTestId ?? null,
      testName: updates.rawTestName ?? null,
      observedAt: updates.observedAt ?? null,
      valueText: updates.rawValue ?? null,
      numericValue: updates.numericValue ?? null,
      unit: updates.unit ?? null,
      referenceRange: updates.referenceRange ?? null,
      abnormalFlag: null,
      status: "NEEDS_REVIEW",
      confirmedObservationId: null,
      createdAt: nowIso(),
      updatedAt: nowIso()
    };
  }

  async confirmParsedObservation(id: string) {
    const observation = await confirmParsedObservation(id);
    return {
      ...mapLabObservationResponse(observation),
      parsedObservationId: id
    };
  }

  async rejectParsedObservation(id: string) {
    const rejectedItem = await rejectParsedObservation(id);
    return mapReviewResponse(rejectedItem);
  }

  async updateParsedObservationStatus(id: string, status: VaultResourceStatus) {
    if (status === "CONFIRMED") {
      const observation = await confirmParsedObservation(id);
      return mapConfirmedObservationToReviewItem(observation, id);
    }

    if (status === "REJECTED") {
      const rejectedItem = await rejectParsedObservation(id);
      return mapReviewResponse(rejectedItem);
    }

    throw unsupported(`updateParsedObservationStatus(${status})`);
  }

  async listConfirmedObservations(
    filters: VaultConfirmedObservationFilters = {}
  ): Promise<VaultObservation[]> {
    if (!filters.testId) {
      throw unsupported("listConfirmedObservations without a testId");
    }

    const trend = await getLabTrend(filters.testId, filters.patientId);
    return mapTrendResponse(trend, filters.testId ?? "").points
      .filter((point) => isInDateRange(point.observedAt, filters))
      .map((trendPoint) => {
      const timestamp = nowIso();

      return {
        resourceType: "Observation",
        observationId: trendPoint.observationId ?? trendPoint.trendPointId,
        patientUserId: filters.patientId ?? null,
        testId: trendPoint.testId,
        testName: trendPoint.testName,
        observedAt: trendPoint.observedAt,
        valueText: trendPoint.valueText,
        numericValue: trendPoint.numericValue,
        unit: trendPoint.unit,
        referenceRange: null,
        abnormalFlag: null,
        status: "CONFIRMED",
        sourceType: trendPoint.sourceType,
        reportId: trendPoint.reportId,
        reportOriginalFilename: trendPoint.reportOriginalFilename,
        labName: trendPoint.labName,
        reportDate: trendPoint.reportDate,
        parsedObservationId: trendPoint.parsedObservationId,
        createdAt: timestamp,
        updatedAt: timestamp
      };
    });
  }

  async saveConfirmedObservation(
    _observation: VaultObservation
  ): Promise<VaultObservation> {
    throw unsupported("saveConfirmedObservation");
  }

  async listTrendPoints(testId: string, filters: VaultTrendFilters = {}) {
    const trend = await this.getTrendForTest(testId, filters);
    return trend.points;
  }

  async listAvailableTrendTests(filters: VaultTrendFilters = {}) {
    const tests = await getTests();
    const trendResults = await Promise.allSettled(
      tests.map((test) => this.getTrendForTest(test.id, filters))
    );

    return tests.flatMap((test, index) => {
      const trendResult = trendResults[index];

      if (trendResult.status !== "fulfilled") {
        return [];
      }

      const availableTrendTest = mapAvailableTrendTest(test, trendResult.value);
      return availableTrendTest.pointCount > 0 ? [availableTrendTest] : [];
    });
  }

  async getTrendForTest(testId: string, filters: VaultTrendFilters = {}) {
    const trend = await getLabTrend(testId, filters.patientId);
    const mappedTrend = mapTrendResponse(trend, testId);

    return {
      ...mappedTrend,
      points: filterTrendPoints(mappedTrend.points, filters)
    };
  }

  async listAuditEvents(filters: VaultAuditEventFilters = {}) {
    const events = await getAuditEvents(filters.patientId);
    const limit = filters.limit ?? 50;

    return events
      .map(mapAuditEvent)
      .filter((event) => !filters.action || event.action === filters.action)
      .filter(
        (event) =>
          !filters.resourceType || event.resourceTypeName === filters.resourceType
      )
      .slice(0, limit);
  }

  async recordAuditEvent(_event: VaultAuditEvent): Promise<VaultAuditEvent> {
    throw unsupported("recordAuditEvent");
  }

  async exportVault(): Promise<SerializedPatientVault> {
    throw unsupported("exportVault");
  }

  async importVault() {
    throw unsupported("importVault");
  }
}

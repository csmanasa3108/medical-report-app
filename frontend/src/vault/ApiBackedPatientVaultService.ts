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
  rejectParsedObservation,
  uploadReport as uploadBackendReport
} from "../api/client";
import type {
  AuditEventResponse,
  LabObservationResponse,
  LabTrendPointResponse,
  ParsedObservationResponse,
  ParsedObservationReviewResponse,
  ReportResponse
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
  VaultReportDocument,
  VaultReportFilters,
  VaultReportUploadMetadata,
  VaultResourceStatus,
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
      const observations = await getParsedObservations(filters.reportId);
      return observations
        .map((item) => mapReportParsedObservation(item, filters.reportId ?? ""))
        .filter((item) => !filters.status || item.status === filters.status);
    }

    const reviewItems = await getParsedObservationReviewQueue(
      filters.patientId,
      filters.status
    );
    return reviewItems.map(mapReviewResponse);
  }

  async saveParsedObservation(
    _item: VaultParsedObservationReviewItem
  ): Promise<VaultParsedObservationReviewItem> {
    throw unsupported("saveParsedObservation");
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
    return trend.points.map((point) => {
      const trendPoint = mapTrendPoint(
        trend.testId,
        trend.testName ?? filters.testId ?? "",
        point
      );
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

  async listTrendPoints(testId: string) {
    const trend = await getLabTrend(testId);
    return trend.points.map((point) =>
      mapTrendPoint(trend.testId, trend.testName ?? testId, point)
    );
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

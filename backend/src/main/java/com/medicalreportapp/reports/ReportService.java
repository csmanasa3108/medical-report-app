package com.medicalreportapp.reports;

import com.medicalreportapp.observations.DefaultUserProvider;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class ReportService {

    private final ReportRepository reportRepository;
    private final DefaultUserProvider defaultUserProvider;

    ReportService(ReportRepository reportRepository, DefaultUserProvider defaultUserProvider) {
        this.reportRepository = reportRepository;
        this.defaultUserProvider = defaultUserProvider;
    }

    @Transactional
    public ReportResponse create(CreateReportRequest request) {
        Report report = new Report(
            UUID.randomUUID(),
            defaultUserProvider.getDefaultUserId(),
            request.originalFilename(),
            request.reportDate(),
            request.labName(),
            ReportStatus.CREATED
        );

        return ReportResponse.from(reportRepository.saveAndFlush(report));
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> findAll() {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(defaultUserProvider.getDefaultUserId())
            .stream()
            .map(ReportResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse findById(UUID reportId) {
        UUID userId = defaultUserProvider.getDefaultUserId();
        return reportRepository.findByIdAndUserId(reportId, userId)
            .map(ReportResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }
}

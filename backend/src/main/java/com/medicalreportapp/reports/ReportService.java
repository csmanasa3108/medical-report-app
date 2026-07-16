package com.medicalreportapp.reports;

import com.medicalreportapp.observations.DefaultUserProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class ReportService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final ReportRepository reportRepository;
    private final DefaultUserProvider defaultUserProvider;
    private final Path reportUploadDirectory;

    ReportService(
        ReportRepository reportRepository,
        DefaultUserProvider defaultUserProvider,
        @Value("${app.uploads.reports-directory:uploads/reports}") String reportUploadDirectory
    ) {
        this.reportRepository = reportRepository;
        this.defaultUserProvider = defaultUserProvider;
        this.reportUploadDirectory = Path.of(reportUploadDirectory);
    }

    @Transactional
    public ReportResponse create(CreateReportRequest request) {
        Report report = new Report(
            UUID.randomUUID(),
            defaultUserProvider.getDefaultUserId(),
            request.originalFilename(),
            request.reportDate(),
            request.labName(),
            null,
            null,
            null,
            null,
            ReportStatus.CREATED
        );

        return ReportResponse.from(reportRepository.saveAndFlush(report));
    }

    @Transactional
    public ReportResponse upload(UploadReportRequest request) {
        MultipartFile file = request.file();
        validateUpload(file);

        UUID reportId = UUID.randomUUID();
        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        String storedFilename = reportId + ".pdf";
        Path storagePath = reportUploadDirectory.resolve(storedFilename);

        try {
            Files.createDirectories(reportUploadDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store report file", exception);
        }

        Report report = new Report(
            reportId,
            defaultUserProvider.getDefaultUserId(),
            originalFilename,
            request.reportDate(),
            request.labName(),
            storedFilename,
            storagePath.toString(),
            PDF_CONTENT_TYPE,
            file.getSize(),
            ReportStatus.UPLOADED
        );

        try {
            return ReportResponse.from(reportRepository.saveAndFlush(report));
        } catch (RuntimeException exception) {
            deleteStoredFile(storagePath);
            throw exception;
        }
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
        return ReportResponse.from(findReportForDefaultUser(reportId));
    }

    @Transactional
    public ReportTextResponse extractText(UUID reportId) {
        Report report = findReportForDefaultUser(reportId);
        Path storagePath = resolveStoragePath(report);

        if (!Files.exists(storagePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored report file not found");
        }

        String extractedText = extractPdfText(storagePath);
        report.markTextExtracted(extractedText, Instant.now());

        return ReportTextResponse.from(reportRepository.saveAndFlush(report));
    }

    @Transactional(readOnly = true)
    public ReportTextResponse findText(UUID reportId) {
        return ReportTextResponse.from(findReportForDefaultUser(reportId));
    }

    private Report findReportForDefaultUser(UUID reportId) {
        UUID userId = defaultUserProvider.getDefaultUserId();
        return reportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private static Path resolveStoragePath(Report report) {
        if (!StringUtils.hasText(report.getStoragePath())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report has no stored file path");
        }
        return Path.of(report.getStoragePath());
    }

    private static String extractPdfText(Path storagePath) {
        try (PDDocument document = Loader.loadPDF(storagePath.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not extract text from stored PDF", exception);
        }
    }

    private static void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF file is required");
        }

        if (!PDF_CONTENT_TYPE.equals(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF uploads are supported");
        }
    }

    private static String cleanOriginalFilename(String originalFilename) {
        String cleanedFilename = StringUtils.getFilename(
            StringUtils.cleanPath(originalFilename == null ? "report.pdf" : originalFilename)
        );
        if (!StringUtils.hasText(cleanedFilename)) {
            return "report.pdf";
        }
        return cleanedFilename.length() <= 255 ? cleanedFilename : cleanedFilename.substring(cleanedFilename.length() - 255);
    }

    private static void deleteStoredFile(Path storagePath) {
        try {
            Files.deleteIfExists(storagePath);
        } catch (IOException ignored) {
            // Best-effort cleanup only; avoid logging sensitive local file paths.
        }
    }
}

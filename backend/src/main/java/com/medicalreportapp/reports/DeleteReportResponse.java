package com.medicalreportapp.reports;

import java.util.UUID;

public record DeleteReportResponse(UUID reportId, String status) {

    static DeleteReportResponse deleted(UUID reportId) {
        return new DeleteReportResponse(reportId, "DELETED");
    }
}

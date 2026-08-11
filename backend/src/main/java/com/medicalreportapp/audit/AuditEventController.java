package com.medicalreportapp.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditEventController {

    private final AuditService auditService;

    AuditEventController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/audit-events")
    public List<AuditEventResponse> findAuditEvents(
        @RequestParam(value = "patientId", required = false) UUID patientId
    ) {
        return auditService.findForCurrentUser(patientId);
    }
}

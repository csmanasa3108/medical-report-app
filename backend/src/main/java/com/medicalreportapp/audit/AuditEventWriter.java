package com.medicalreportapp.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditEventWriter {

    private final AuditEventRepository auditEventRepository;

    AuditEventWriter(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(AuditEvent auditEvent) {
        auditEventRepository.saveAndFlush(auditEvent);
    }
}

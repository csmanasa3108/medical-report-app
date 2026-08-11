package com.medicalreportapp.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByPatientUserIdOrderByCreatedAtDesc(UUID patientUserId, Pageable pageable);
}

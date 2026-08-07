package com.medicalreportapp.observations;

import com.medicalreportapp.users.UserAccessService;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class DefaultUserProvider {

    private final UserAccessService userAccessService;

    public DefaultUserProvider(UserAccessService userAccessService) {
        this.userAccessService = userAccessService;
    }

    public UUID getDefaultUserId() {
        return userAccessService.resolveReadablePatientId(null);
    }

    public UUID getCurrentUserId() {
        return userAccessService.getCurrentUserId();
    }

    public UUID resolveReadablePatientId(UUID requestedPatientId) {
        return userAccessService.resolveReadablePatientId(requestedPatientId);
    }

    public UUID requireCurrentUserCanWritePatientData() {
        return userAccessService.requireCurrentUserCanWritePatientData();
    }

    public void requireCurrentUserCanWritePatientData(UUID patientUserId) {
        userAccessService.requireCurrentUserCanWritePatientData(patientUserId);
    }

    public void requireCurrentUserCanReadPatientData(UUID patientUserId) {
        userAccessService.requireCurrentUserCanReadPatientData(patientUserId);
    }
}

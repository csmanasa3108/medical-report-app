package com.medicalreportapp.users;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserContextResolver {

    public static final UUID DEMO_PATIENT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final UUID DEMO_CLINICIAN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AppUserRepository appUserRepository;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    UserContextResolver(AppUserRepository appUserRepository, ObjectProvider<HttpServletRequest> requestProvider) {
        this.appUserRepository = appUserRepository;
        this.requestProvider = requestProvider;
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public UUID getCurrentPatientUserId() {
        AppUser currentUser = getCurrentUser();
        if (currentUser.getRole() == AppUserRole.PATIENT) {
            return currentUser.getId();
        }

        return DEMO_PATIENT_USER_ID;
    }

    public AppUser getCurrentUser() {
        UUID userId = resolveRequestedUserId();
        return appUserRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UUID resolveRequestedUserId() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null || !StringUtils.hasText(request.getHeader(USER_ID_HEADER))) {
            return DEMO_PATIENT_USER_ID;
        }

        try {
            return UUID.fromString(request.getHeader(USER_ID_HEADER));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-User-Id header");
        }
    }
}

package com.medicalreportapp.users;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserContextResolver {

    public static final UUID DEMO_PATIENT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final UUID DEMO_CLINICIAN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String DEV_AUTH_MODE = "dev";

    private final AppUserRepository appUserRepository;
    private final ObjectProvider<HttpServletRequest> requestProvider;
    private final String authMode;

    UserContextResolver(
        AppUserRepository appUserRepository,
        ObjectProvider<HttpServletRequest> requestProvider,
        @Value("${sovera.auth.mode:dev}") String authMode
    ) {
        this.appUserRepository = appUserRepository;
        this.requestProvider = requestProvider;
        this.authMode = authMode;
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
        if (!isDevAuthMode()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }

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

    private boolean isDevAuthMode() {
        return DEV_AUTH_MODE.equalsIgnoreCase(authMode);
    }
}

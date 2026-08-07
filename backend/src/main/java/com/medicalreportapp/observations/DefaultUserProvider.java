package com.medicalreportapp.observations;

import com.medicalreportapp.users.UserContextResolver;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class DefaultUserProvider {

    private final UserContextResolver userContextResolver;

    public DefaultUserProvider(UserContextResolver userContextResolver) {
        this.userContextResolver = userContextResolver;
    }

    public UUID getDefaultUserId() {
        return userContextResolver.getCurrentPatientUserId();
    }

    public UUID getCurrentUserId() {
        return userContextResolver.getCurrentUserId();
    }
}

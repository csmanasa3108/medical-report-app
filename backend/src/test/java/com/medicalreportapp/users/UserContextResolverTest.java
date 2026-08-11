package com.medicalreportapp.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class UserContextResolverTest {

    @Test
    void devModeUsesUserIdHeader() {
        UUID userId = UserContextResolver.DEMO_CLINICIAN_USER_ID;
        AppUser clinician = new AppUser(userId, "clinician.demo@example.local", "Demo Clinician", AppUserRole.CLINICIAN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId.toString());
        AppUserRepository appUserRepository = repositoryReturning(userId, clinician);
        ObjectProvider<HttpServletRequest> requestProvider = requestProvider(request);
        UserContextResolver resolver = new UserContextResolver(appUserRepository, requestProvider, "dev");

        assertThat(resolver.getCurrentUser()).isEqualTo(clinician);
    }

    @Test
    void devModeFallsBackToDemoPatientWhenUserIdHeaderIsMissing() {
        AppUser patient = new AppUser(
            UserContextResolver.DEMO_PATIENT_USER_ID,
            "patient.demo@example.local",
            "Demo Patient",
            AppUserRole.PATIENT
        );
        AppUserRepository appUserRepository = repositoryReturning(UserContextResolver.DEMO_PATIENT_USER_ID, patient);
        ObjectProvider<HttpServletRequest> requestProvider = requestProvider(new MockHttpServletRequest());
        UserContextResolver resolver = new UserContextResolver(appUserRepository, requestProvider, "dev");

        assertThat(resolver.getCurrentUser()).isEqualTo(patient);
    }

    @Test
    void nonDevModeRejectsDevUserIdHeader() {
        AppUserRepository appUserRepository = failingRepository();
        ObjectProvider<HttpServletRequest> requestProvider = failingRequestProvider();
        UserContextResolver resolver = new UserContextResolver(appUserRepository, requestProvider, "token");

        assertThatThrownBy(resolver::getCurrentUser)
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static AppUserRepository repositoryReturning(UUID expectedUserId, AppUser appUser) {
        return repository((methodName, args) -> {
            if ("findById".equals(methodName)) {
                return expectedUserId.equals(args[0]) ? Optional.of(appUser) : Optional.empty();
            }

            throw new AssertionError("Unexpected repository call: " + methodName);
        });
    }

    private static AppUserRepository failingRepository() {
        return repository((methodName, args) -> {
            throw new AssertionError("Repository should not be called in non-dev auth mode");
        });
    }

    private static AppUserRepository repository(RepositoryCallHandler callHandler) {
        return (AppUserRepository) Proxy.newProxyInstance(
            AppUserRepository.class.getClassLoader(),
            new Class<?>[] { AppUserRepository.class },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "AppUserRepository test proxy";
                        default -> throw new AssertionError("Unexpected object method: " + method.getName());
                    };
                }

                return callHandler.handle(method.getName(), args);
            }
        );
    }

    private static ObjectProvider<HttpServletRequest> requestProvider(HttpServletRequest request) {
        return new ObjectProvider<>() {
            @Override
            public HttpServletRequest getObject() {
                return request;
            }

            @Override
            public HttpServletRequest getObject(Object... args) {
                return request;
            }

            @Override
            public HttpServletRequest getIfAvailable() {
                return request;
            }
        };
    }

    private static ObjectProvider<HttpServletRequest> failingRequestProvider() {
        return new ObjectProvider<>() {
            @Override
            public HttpServletRequest getObject() {
                throw new AssertionError("Request should not be resolved in non-dev auth mode");
            }

            @Override
            public HttpServletRequest getObject(Object... args) {
                throw new AssertionError("Request should not be resolved in non-dev auth mode");
            }

            @Override
            public HttpServletRequest getIfAvailable() {
                throw new AssertionError("Request should not be resolved in non-dev auth mode");
            }
        };
    }

    @FunctionalInterface
    private interface RepositoryCallHandler {
        Object handle(String methodName, Object[] args);
    }
}

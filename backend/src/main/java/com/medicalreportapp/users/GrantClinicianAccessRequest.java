package com.medicalreportapp.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrantClinicianAccessRequest(
    @NotBlank @Email @Size(max = 255) String clinicianEmail
) {
}

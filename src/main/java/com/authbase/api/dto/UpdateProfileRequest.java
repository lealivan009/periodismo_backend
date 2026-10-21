package com.authbase.api.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
        String firstName,

        @Size(min = 2, max = 80, message = "El apellido debe tener entre 2 y 80 caracteres")
        String lastName,

        @Size(max = 20, message = "El documento es demasiado largo")
        String documentNumber,

        @Size(max = 30, message = "El celular es demasiado largo")
        String phone,

        @Past(message = "La fecha de nacimiento no puede ser futura")
        LocalDate birthDate,

        @Size(max = 160, message = "El domicilio es demasiado largo")
        String street,

        @Size(max = 80, message = "La ciudad es demasiado larga")
        String city,

        @Size(max = 80, message = "La provincia es demasiado larga")
        String province,

        @Size(max = 20, message = "El código postal es demasiado largo")
        String postalCode,

        @Size(max = 4000, message = "La descripción puede tener hasta 4000 caracteres")
        String bio,

        @Size(max = 120, message = "La especialidad es demasiado larga")
        String specialty,

        @Size(max = 300, message = "El enlace de X es demasiado largo")
        String xUrl,

        @Size(max = 300, message = "El enlace de Instagram es demasiado largo")
        String instagramUrl,

        @Size(max = 300, message = "El enlace de Facebook es demasiado largo")
        String facebookUrl,

        @Size(max = 30, message = "El usuario puede tener hasta 30 caracteres")
        String username
) {
}

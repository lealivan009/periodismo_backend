package com.authbase.api.dto;

import com.authbase.domain.Role;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserRequest(
        @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
        String firstName,

        @Size(min = 2, max = 80, message = "El apellido debe tener entre 2 y 80 caracteres")
        String lastName,
        Role role,
        Boolean enabled,

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
        String postalCode
) {
}

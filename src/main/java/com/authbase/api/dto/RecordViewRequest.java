package com.authbase.api.dto;

import jakarta.validation.constraints.Size;

public record RecordViewRequest(
        @Size(max = 36, message = "El identificador de visita no es válido")
        String visitorId
) {
}

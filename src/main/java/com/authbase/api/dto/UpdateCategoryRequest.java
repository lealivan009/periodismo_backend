package com.authbase.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Size(max = 80, message = "El nombre es demasiado largo")
        String name,
        Boolean active
) {
}

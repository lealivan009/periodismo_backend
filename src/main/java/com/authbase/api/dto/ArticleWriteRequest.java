package com.authbase.api.dto;

import jakarta.validation.constraints.Size;

public record ArticleWriteRequest(
        @Size(max = 180, message = "El título es demasiado largo")
        String title,

        @Size(max = 100000, message = "El texto es demasiado largo")
        String content,

        @Size(max = 400, message = "El copete es demasiado largo")
        String excerpt,

        String categoryId
) {
}

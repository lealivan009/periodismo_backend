package com.authbase.api.dto;

import com.authbase.domain.Category;

public record CategoryResponse(
        String id,
        String name,
        String slug,
        boolean active
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.id, category.name, category.slug, category.active);
    }
}

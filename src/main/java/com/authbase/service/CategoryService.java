package com.authbase.service;

import com.authbase.api.dto.CategoryResponse;
import com.authbase.api.dto.CreateCategoryRequest;
import com.authbase.api.dto.UpdateCategoryRequest;
import com.authbase.domain.Category;
import com.authbase.error.ApiException;
import com.authbase.helpers.Slugs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class CategoryService {

    public List<CategoryResponse> listActive() {
        return Category.<Category>list("active = true order by name")
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public List<CategoryResponse> listAll() {
        return Category.<Category>list("order by name")
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (Category.count("lower(name) = ?1", name.toLowerCase()) > 0) {
            throw ApiException.conflict("CATEGORY_EXISTS", "Ya existe una categoría con ese nombre");
        }
        String slug = uniqueSlug(Slugs.from(name), null);
        Category category = new Category();
        category.name = name;
        category.slug = slug;
        category.active = true;
        category.persist();
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(String id, UpdateCategoryRequest request) {
        Category category = require(id);
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (Category.count("lower(name) = ?1 and id <> ?2", name.toLowerCase(), category.id) > 0) {
                throw ApiException.conflict("CATEGORY_EXISTS", "Ya existe una categoría con ese nombre");
            }
            category.name = name;
        }
        if (request.active() != null) {
            category.active = request.active();
        }
        return CategoryResponse.from(category);
    }

    public Category requireActive(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return Category.<Category>find("slug = ?1 and active = true", "deportes")
                    .firstResultOptional()
                    .orElseThrow(() -> ApiException.badRequest("CATEGORY_REQUIRED", "Elegí una categoría"));
        }
        Category category = Category.findById(categoryId);
        if (category == null || !category.active) {
            throw ApiException.badRequest("CATEGORY_INVALID", "La categoría no está disponible");
        }
        return category;
    }

    private Category require(String id) {
        Category category = Category.findById(id);
        if (category == null) {
            throw ApiException.notFound("CATEGORY_NOT_FOUND", "No se encontró la categoría");
        }
        return category;
    }

    private String uniqueSlug(String base, String currentId) {
        return Slugs.unique(base, candidate -> {
            Category existing = Category.find("slug", candidate).firstResult();
            return existing != null && (currentId == null || !existing.id.equals(currentId));
        });
    }
}

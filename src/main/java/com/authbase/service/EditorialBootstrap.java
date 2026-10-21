package com.authbase.service;

import com.authbase.domain.Category;
import com.authbase.domain.Role;
import com.authbase.domain.User;
import com.authbase.helpers.Slugs;
import com.authbase.helpers.Usernames;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class EditorialBootstrap {

    private final EntityManager entityManager;

    public EditorialBootstrap(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE + 10) StartupEvent event) {
        entityManager.createNativeQuery("UPDATE users SET role = 'READER' WHERE role = 'USER'").executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE users SET verification_status = 'NONE' WHERE verification_status IS NULL"
        ).executeUpdate();
        widenArticleContent();
        widenJournalistBio();
        seedSections();
        seedUsernames();
    }

    private void seedUsernames() {
        List<User> pending = User.list("role = ?1 and username is null", Role.JOURNALIST);
        for (User user : pending) {
            Usernames.ensure(user);
            entityManager.flush();
        }
    }

    private void seedSections() {
        List<String> sections = List.of(
                "Política",
                "Economía",
                "Sociedad",
                "Internacional",
                "Deportes",
                "Cultura",
                "Espectáculos",
                "Tecnología"
        );
        for (String name : sections) {
            String slug = Slugs.from(name);
            if (Category.count("slug = ?1", slug) > 0) {
                continue;
            }
            Category category = new Category();
            category.name = name;
            category.slug = slug;
            category.active = true;
            category.persist();
        }
    }

    private void widenArticleContent() {
        @SuppressWarnings("unchecked")
        List<Object> lengths = entityManager.createNativeQuery("""
                SELECT CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'articles' AND LOWER(COLUMN_NAME) = 'content'
                """).getResultList();
        if (lengths.isEmpty() || !(lengths.get(0) instanceof Number length) || length.longValue() >= 1_000_000L) {
            return;
        }
        entityManager.createNativeQuery("ALTER TABLE articles MODIFY content LONGTEXT NOT NULL").executeUpdate();
    }

    private void widenJournalistBio() {
        @SuppressWarnings("unchecked")
        List<Object> lengths = entityManager.createNativeQuery("""
                SELECT CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'users' AND LOWER(COLUMN_NAME) = 'bio'
                """).getResultList();
        if (lengths.isEmpty() || !(lengths.get(0) instanceof Number length) || length.longValue() >= 4_000L) {
            return;
        }
        entityManager.createNativeQuery("ALTER TABLE users MODIFY bio TEXT").executeUpdate();
    }
}

package com.authbase.service;

import com.authbase.api.dto.ArticleResponse;
import com.authbase.api.dto.ArticleSummary;
import com.authbase.api.dto.ArticleWriteRequest;
import com.authbase.domain.Article;
import com.authbase.domain.ArticlePublishedEvent;
import com.authbase.domain.ArticleStatus;
import com.authbase.domain.Category;
import com.authbase.domain.Follow;
import com.authbase.domain.Role;
import com.authbase.domain.User;
import com.authbase.error.ApiException;
import com.authbase.helpers.HtmlContent;
import com.authbase.helpers.Slugs;
import com.authbase.helpers.UserProfiles;
import com.authbase.service.storage.MediaStore;
import com.authbase.service.storage.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ArticleService {

    private final CategoryService categoryService;
    private final MediaStore mediaStore;
    private final Event<ArticlePublishedEvent> published;

    public ArticleService(CategoryService categoryService, MediaStore mediaStore, Event<ArticlePublishedEvent> published) {
        this.categoryService = categoryService;
        this.mediaStore = mediaStore;
        this.published = published;
    }

    @Transactional
    public List<ArticleSummary> listPublished(String categorySlug) {
        if (categorySlug == null || categorySlug.isBlank()) {
            return Article.<Article>find("status = ?1 order by publishedAt desc", ArticleStatus.PUBLISHED)
                    .page(0, 40)
                    .list()
                    .stream()
                    .map(article -> ArticleResponse.from(article).summary())
                    .toList();
        }
        Category category = Category.find("slug = ?1 and active = true", categorySlug.trim()).firstResult();
        if (category == null) {
            throw ApiException.notFound("CATEGORY_NOT_FOUND", "No se encontró la categoría");
        }
        return Article.<Article>find(
                        "status = ?1 and category.id = ?2 order by publishedAt desc",
                        ArticleStatus.PUBLISHED,
                        category.id
                )
                .page(0, 40)
                .list()
                .stream()
                .map(article -> ArticleResponse.from(article).summary())
                .toList();
    }

    @Transactional
    public List<ArticleSummary> listFollowed(User viewer) {
        List<String> journalistIds = Follow.<Follow>find("follower.id", viewer.id)
                .list()
                .stream()
                .map(follow -> follow.journalist.id)
                .toList();
        if (journalistIds.isEmpty()) {
            return List.of();
        }
        return Article.<Article>find(
                        "status = ?1 and author.id in ?2 order by publishedAt desc",
                        ArticleStatus.PUBLISHED,
                        journalistIds
                )
                .page(0, 40)
                .list()
                .stream()
                .map(article -> ArticleResponse.from(article).summary())
                .toList();
    }

    @Transactional
    public long recordView(String slug) {
        Article article = Article.<Article>find("slug = ?1 and status = ?2", slug, ArticleStatus.PUBLISHED)
                .firstResultOptional()
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "No se encontró la nota"));
        article.viewCount = views(article) + 1;
        return views(article);
    }

    @Transactional
    public ArticleResponse publishedBySlug(String slug) {
        Article article = Article.<Article>find("slug = ?1 and status = ?2", slug, ArticleStatus.PUBLISHED)
                .firstResultOptional()
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "No se encontró la nota"));
        return ArticleResponse.from(article);
    }

    @Transactional
    public List<ArticleResponse> mine(User author) {
        return Article.<Article>find("author.id = ?1 order by updatedAt desc", author.id)
                .page(0, 50)
                .list()
                .stream()
                .map(ArticleResponse::from)
                .toList();
    }

    public long countPublished(String authorId) {
        return Article.count("author.id = ?1 and status = ?2", authorId, ArticleStatus.PUBLISHED);
    }

    @Transactional
    public List<ArticleSummary> byAuthorPublished(String authorId) {
        return Article.<Article>find(
                        "author.id = ?1 and status = ?2 order by publishedAt desc",
                        authorId,
                        ArticleStatus.PUBLISHED
                )
                .page(0, 30)
                .list()
                .stream()
                .map(article -> ArticleResponse.from(article).summary())
                .toList();
    }

    @Transactional
    public ArticleResponse get(String id, User actor) {
        return ArticleResponse.from(requireVisible(id, actor));
    }

    @Transactional
    public ArticleResponse create(User author, ArticleWriteRequest request) {
        String title = requireTitle(request.title());
        String content = requireContent(request.content());
        Article article = new Article();
        article.title = title;
        article.content = content;
        article.excerpt = excerpt(request.excerpt(), content);
        article.slug = allocateSlug(title, null);
        article.author = author;
        article.category = categoryService.requireActive(request.categoryId());
        article.status = ArticleStatus.DRAFT;
        article.persist();
        return ArticleResponse.from(article);
    }

    @Transactional
    public ArticleResponse update(String id, User author, ArticleWriteRequest request) {
        Article article = requireOwn(id, author);
        if (request.title() != null) {
            article.title = requireTitle(request.title());
            if (article.status == ArticleStatus.DRAFT) {
                article.slug = allocateSlug(article.title, article.id);
            }
        }
        if (request.content() != null) {
            article.content = requireContent(request.content());
        }
        if (request.excerpt() != null || request.content() != null) {
            article.excerpt = excerpt(
                    request.excerpt() != null ? request.excerpt() : article.excerpt,
                    article.content
            );
        }
        if (request.categoryId() != null && !request.categoryId().isBlank()) {
            article.category = categoryService.requireActive(request.categoryId());
        }
        return ArticleResponse.from(article);
    }

    @Transactional
    public ArticleResponse publish(String id, User author) {
        Article article = requireOwn(id, author);
        if (HtmlContent.isBlank(article.content) || article.title == null || article.title.isBlank()) {
            throw ApiException.badRequest("ARTICLE_INCOMPLETE", "La nota necesita título y texto para publicarse");
        }
        if (article.status == ArticleStatus.PUBLISHED) {
            return ArticleResponse.from(article);
        }
        article.status = ArticleStatus.PUBLISHED;
        article.publishedAt = Instant.now();
        published.fire(new ArticlePublishedEvent(article.id));
        return ArticleResponse.from(article);
    }

    @Transactional
    public ArticleResponse archive(String id, User actor) {
        Article article = Article.findById(id);
        if (article == null) {
            throw ApiException.notFound("ARTICLE_NOT_FOUND", "No se encontró la nota");
        }
        boolean owner = article.author.id.equals(actor.id);
        if (!owner && actor.role != Role.ADMIN) {
            throw ApiException.forbidden("FORBIDDEN", "No podés archivar esta nota");
        }
        article.status = ArticleStatus.ARCHIVED;
        return ArticleResponse.from(article);
    }

    @Transactional
    public ArticleResponse cover(String id, User author, UploadedFile file) {
        Article article = requireOwn(id, author);
        if (file == null || file.bytes() == null || file.bytes().length == 0) {
            throw ApiException.badRequest("COVER_REQUIRED", "Elegí una imagen de tapa");
        }
        String previous = article.coverImageUrl;
        article.coverImageUrl = mediaStore.saveImage("covers", article.id, file.fileName(), file.contentType(), file.bytes());
        mediaStore.deleteQuietly(previous);
        return ArticleResponse.from(article);
    }

    @Transactional
    public String coverPath(String articleId) {
        Article article = Article.findById(articleId);
        if (article == null || article.coverImageUrl == null || article.coverImageUrl.isBlank()) {
            throw ApiException.notFound("FILE_NOT_FOUND", "No se encontró la imagen");
        }
        return article.coverImageUrl;
    }

    private Article requireVisible(String id, User actor) {
        Article article = Article.findById(id);
        if (article == null) {
            throw ApiException.notFound("ARTICLE_NOT_FOUND", "No se encontró la nota");
        }
        if (article.status == ArticleStatus.PUBLISHED) {
            return article;
        }
        if (actor != null && (actor.role == Role.ADMIN || actor.id.equals(article.author.id))) {
            return article;
        }
        throw ApiException.notFound("ARTICLE_NOT_FOUND", "No se encontró la nota");
    }

    private Article requireOwn(String id, User author) {
        Article article = Article.findById(id);
        if (article == null || !article.author.id.equals(author.id)) {
            throw ApiException.notFound("ARTICLE_NOT_FOUND", "No se encontró la nota");
        }
        return article;
    }

    private String allocateSlug(String title, String currentId) {
        return Slugs.unique(Slugs.from(title), candidate -> {
            Article existing = Article.find("slug", candidate).firstResult();
            return existing != null && (currentId == null || !existing.id.equals(currentId));
        });
    }

    private static long views(Article article) {
        return article.viewCount == null ? 0L : article.viewCount;
    }

    private static String requireTitle(String title) {
        String clean = UserProfiles.clean(title);
        if (clean == null || clean.length() < 4) {
            throw ApiException.badRequest("TITLE_REQUIRED", "El título debe tener al menos 4 caracteres");
        }
        if (clean.length() > 180) {
            throw ApiException.badRequest("TITLE_REQUIRED", "El título es demasiado largo");
        }
        return clean;
    }

    private static String requireContent(String content) {
        HtmlContent.requireLength(content);
        String clean = HtmlContent.clean(content);
        if (HtmlContent.isBlank(clean)) {
            throw ApiException.badRequest("CONTENT_REQUIRED", "Escribí el texto de la nota");
        }
        return clean;
    }

    private static String excerpt(String provided, String content) {
        String clean = UserProfiles.clean(provided);
        if (clean != null) {
            return clean.length() > 400 ? clean.substring(0, 400) : clean;
        }
        String text = content == null ? "" : content
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > 180) {
            return text.substring(0, 177) + "…";
        }
        return text;
    }
}

package com.authbase.service;

import com.authbase.api.dto.AdminOverviewResponse;
import com.authbase.api.dto.ArticleResponse;
import com.authbase.api.dto.ArticleSummary;
import com.authbase.api.dto.ArticleWriteRequest;
import com.authbase.api.dto.AuthorStatsResponse;
import com.authbase.domain.Article;
import com.authbase.domain.ArticlePublishedEvent;
import com.authbase.domain.ArticleStatus;
import com.authbase.domain.ArticleView;
import com.authbase.domain.Category;
import com.authbase.domain.Follow;
import com.authbase.domain.Role;
import com.authbase.domain.Subscription;
import com.authbase.domain.User;
import com.authbase.domain.VerificationStatus;
import com.authbase.error.ApiException;
import com.authbase.helpers.HtmlContent;
import com.authbase.helpers.Slugs;
import com.authbase.helpers.UserProfiles;
import com.authbase.service.storage.MediaStore;
import com.authbase.service.storage.UploadedFile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@ApplicationScoped
public class ArticleService {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Pattern VISITOR_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

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
    public long recordView(String slug, User viewer, String visitorId) {
        Article article = Article.<Article>find("slug = ?1 and status = ?2", slug, ArticleStatus.PUBLISHED)
                .firstResultOptional()
                .orElseThrow(() -> ApiException.notFound("ARTICLE_NOT_FOUND", "No se encontró la nota"));
        String viewerKey = viewer != null ? "u:" + viewer.id : anonymousKey(visitorId);
        if (viewerKey == null) {
            return views(article);
        }
        if (rememberView(article, viewerKey)) {
            article.viewCount = views(article) + 1;
        }
        return views(article);
    }

    @Transactional
    public AuthorStatsResponse stats(User author) {
        long totalViews = Article.<Article>find("author.id", author.id)
                .list()
                .stream()
                .mapToLong(ArticleService::views)
                .sum();
        YearMonth current = YearMonth.now(ZONE);
        YearMonth startMonth = current.minusMonths(11);
        Instant from = startMonth.atDay(1).atStartOfDay(ZONE).toInstant();
        Map<YearMonth, Long> byMonth = new HashMap<>();
        for (ArticleView view : ArticleView.<ArticleView>find(
                "article.author.id = ?1 and createdAt >= ?2",
                author.id,
                from
        ).list()) {
            YearMonth month = YearMonth.from(view.createdAt.atZone(ZONE));
            byMonth.merge(month, 1L, Long::sum);
        }
        List<AuthorStatsResponse.MonthStat> months = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            YearMonth month = startMonth.plusMonths(index);
            months.add(new AuthorStatsResponse.MonthStat(
                    month.getYear(),
                    month.getMonthValue(),
                    byMonth.getOrDefault(month, 0L)
            ));
        }
        List<AuthorStatsResponse.ArticleStat> articles = Article.<Article>find(
                        "author.id = ?1 and status = ?2 order by viewCount desc, publishedAt desc",
                        author.id,
                        ArticleStatus.PUBLISHED
                )
                .page(0, 20)
                .list()
                .stream()
                .map(article -> new AuthorStatsResponse.ArticleStat(
                        article.id,
                        article.title,
                        article.slug,
                        views(article)
                ))
                .toList();
        return new AuthorStatsResponse(
                totalViews,
                byMonth.getOrDefault(current, 0L),
                countPublished(author.id),
                Follow.count("journalist.id", author.id),
                Subscription.count("journalist.id", author.id),
                months,
                articles,
                authorName(author),
                author.username,
                author.role == Role.JOURNALIST
                        && author.verificationStatus == VerificationStatus.APPROVED
                        && author.enabled
        );
    }

    @Transactional
    public AdminOverviewResponse overview() {
        long totalViews = Article.<Article>findAll().stream().mapToLong(ArticleService::views).sum();
        YearMonth current = YearMonth.now(ZONE);
        YearMonth startMonth = current.minusMonths(11);
        Instant from = startMonth.atDay(1).atStartOfDay(ZONE).toInstant();
        Map<YearMonth, Long> byMonth = new HashMap<>();
        for (ArticleView view : ArticleView.<ArticleView>find("createdAt >= ?1", from).list()) {
            YearMonth month = YearMonth.from(view.createdAt.atZone(ZONE));
            byMonth.merge(month, 1L, Long::sum);
        }
        List<AuthorStatsResponse.MonthStat> months = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            YearMonth month = startMonth.plusMonths(index);
            months.add(new AuthorStatsResponse.MonthStat(
                    month.getYear(),
                    month.getMonthValue(),
                    byMonth.getOrDefault(month, 0L)
            ));
        }
        Map<String, Tally> byAuthor = new HashMap<>();
        for (Article article : Article.<Article>find("status", ArticleStatus.PUBLISHED).list()) {
            Tally tally = byAuthor.computeIfAbsent(article.author.id, ignored -> new Tally(article.author));
            tally.views += views(article);
            tally.articles++;
        }
        List<AdminOverviewResponse.AuthorRank> ranking = byAuthor.values().stream()
                .sorted((left, right) -> Long.compare(right.views, left.views))
                .limit(8)
                .map(tally -> new AdminOverviewResponse.AuthorRank(
                        tally.author.id,
                        authorName(tally.author),
                        tally.author.username,
                        tally.views,
                        tally.articles,
                        tally.author.role == Role.JOURNALIST
                                && tally.author.verificationStatus == VerificationStatus.APPROVED
                                && tally.author.enabled
                ))
                .toList();
        return new AdminOverviewResponse(
                totalViews,
                byMonth.getOrDefault(current, 0L),
                Article.count("status", ArticleStatus.PUBLISHED),
                User.count(
                        "role = ?1 and verificationStatus = ?2 and enabled = true and deletedAt is null",
                        Role.JOURNALIST,
                        VerificationStatus.APPROVED
                ),
                User.count(
                        "role = ?1 and verificationStatus = ?2 and deletedAt is null",
                        Role.JOURNALIST,
                        VerificationStatus.PENDING
                ),
                User.count("role = ?1 and enabled = true and deletedAt is null", Role.READER),
                months,
                ranking
        );
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

    private boolean rememberView(Article article, String viewerKey) {
        String articleId = article.id;
        AtomicBoolean inserted = new AtomicBoolean(false);
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                if (ArticleView.count("article.id = ?1 and viewerKey = ?2", articleId, viewerKey) > 0) {
                    return;
                }
                Article managed = Article.findById(articleId);
                ArticleView view = new ArticleView();
                view.article = managed;
                view.viewerKey = viewerKey;
                view.persistAndFlush();
                inserted.set(true);
            });
        } catch (RuntimeException exception) {
            if (isDuplicateView(exception)) {
                return false;
            }
            throw exception;
        }
        return inserted.get();
    }

    private static boolean isDuplicateView(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PersistenceException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("uk_article_view_viewer") || message.contains("Duplicate"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String anonymousKey(String visitorId) {
        if (visitorId == null || !VISITOR_ID.matcher(visitorId).matches()) {
            return null;
        }
        return "v:" + visitorId.toLowerCase(java.util.Locale.ROOT);
    }

    private static final class Tally {
        private final User author;
        private long views;
        private long articles;

        private Tally(User author) {
            this.author = author;
        }
    }

    private static String authorName(User author) {
        String first = author.firstName == null ? "" : author.firstName.trim();
        String last = author.lastName == null ? "" : author.lastName.trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? author.email : name;
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

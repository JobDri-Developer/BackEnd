package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DefaultFewShotSearchService implements FewShotSearchService {
    private static final long QUERY_EMBEDDING_CACHE_CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final long DEFAULT_SELECTION_IN_FLIGHT_WAIT_TIMEOUT_MILLIS = 20_000L;
    private static final long DEFAULT_QUERY_EMBEDDING_IN_FLIGHT_WAIT_TIMEOUT_MILLIS = 20_000L;
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+");

    private final FewShotCaseStore caseStore;
    private final FewShotSearchTextBuilder textBuilder;
    private final CohereEmbeddingClient cohereEmbeddingClient;
    private final FewShotProperties properties;
    private final FewShotMetricsRecorder metricsRecorder;
    private final Map<String, SelectionCacheEntry> selectionCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<SelectionCacheEntry>> selectionInFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean selectionCacheCleanupInProgress = new AtomicBoolean();
    private final AtomicBoolean selectionCacheCleanupRequested = new AtomicBoolean();
    private final Map<String, QueryEmbeddingCacheEntry> queryEmbeddingCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<QueryEmbeddingCacheEntry>> queryEmbeddingInFlight =
            new ConcurrentHashMap<>();
    private final AtomicLong queryEmbeddingCacheNextCleanupAt = new AtomicLong();
    private final AtomicBoolean queryEmbeddingCacheCleanupInProgress = new AtomicBoolean();
    private final Map<String, DocumentEmbeddingCacheEntry> documentEmbeddingCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<DocumentEmbeddingCacheEntry>> documentEmbeddingInFlight =
            new ConcurrentHashMap<>();
    private final AtomicBoolean documentEmbeddingCacheCleanupInProgress = new AtomicBoolean();
    private final AtomicBoolean documentEmbeddingCacheCleanupRequested = new AtomicBoolean();

    @Autowired
    public DefaultFewShotSearchService(
            FewShotCaseStore caseStore,
            FewShotSearchTextBuilder textBuilder,
            CohereEmbeddingClient cohereEmbeddingClient,
            FewShotProperties properties,
            FewShotMetricsRecorder metricsRecorder
    ) {
        this.caseStore = caseStore;
        this.textBuilder = textBuilder;
        this.cohereEmbeddingClient = cohereEmbeddingClient;
        this.properties = properties;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public List<SelectedFewShotCase> searchRelevantFewShots(FewShotSearchQuery query, int topK) {
        if (!properties.isDynamicSelectionEnabled()) {
            log.debug("dynamic few-shot selection disabled.");
            return List.of();
        }
        int requestedTopK = topK > 0 ? topK : properties.getSearch().getTopK();
        long startedAt = System.nanoTime();
        List<FewShotCase> activeCases = caseStore.loadActiveCases();
        String datasetFingerprint = datasetFingerprint(activeCases);
        String cacheKey = selectionCacheKey(query, requestedTopK, datasetFingerprint);
        SelectionCacheEntry cached = readSelectionCache(cacheKey);
        if (cached != null) {
            return cachedSelection(cached, startedAt, "cache hit");
        }

        CompletableFuture<SelectionCacheEntry> created = new CompletableFuture<>();
        CompletableFuture<SelectionCacheEntry> existing = selectionInFlight.putIfAbsent(cacheKey, created);
        if (existing != null) {
            return cachedSelection(awaitSelection(existing), startedAt, "in-flight reuse");
        }
        SelectionCacheEntry cachedAfterClaim = readSelectionCache(cacheKey, false);
        if (cachedAfterClaim != null) {
            created.complete(cachedAfterClaim);
            selectionInFlight.remove(cacheKey, created);
            return cachedSelection(cachedAfterClaim, startedAt, "cache hit after claim");
        }

        try {
            List<FewShotCase> candidates = localPrefilter(activeCases, query);
            List<SelectedFewShotCase> selected = selectWithCohere(query, candidates, requestedTopK);
            FewShotSelectionMode selectionMode = selected.isEmpty()
                    ? FewShotSelectionMode.STATIC_FALLBACK
                    : FewShotSelectionMode.EMBEDDING;
            int minimumSelectedCount = Math.max(
                    1,
                    Math.min(properties.getSearch().getMinimumSelectedCount(), requestedTopK)
            );
            if (selected.size() < minimumSelectedCount && properties.isFallbackEnabled()) {
                selected = selectLocally(query, candidates, requestedTopK, "local-fallback");
                if (!selected.isEmpty()) {
                    selectionMode = FewShotSelectionMode.LOCAL_FALLBACK;
                }
            }
            SelectionCacheEntry entry = new SelectionCacheEntry(selected, selectionMode, expiresAt());
            if (properties.isCacheEnabled()) {
                selectionCache.put(cacheKey, entry);
                maintainSelectionCache(true);
            }
            created.complete(entry);
            recordMetrics(selectionMode, false, selected.size(), startedAt);
            log.info(
                    "dynamic few-shot selection completed. enabled=true, selectionMode={}, cacheHit=false, totalCandidates={}, filteredCandidates={}, selectedIds={}, sources={}, scores={}, datasetVersion={}, latencyMs={}",
                    selectionMode,
                    activeCases.size(),
                    candidates.size(),
                    selected.stream().map(item -> item.fewShotCase().id()).toList(),
                    selected.stream().map(item -> item.fewShotCase().source()).toList(),
                    selected.stream().map(item -> "%.4f".formatted(item.score())).toList(),
                    properties.getDatasetVersion(),
                    (System.nanoTime() - startedAt) / 1_000_000
            );
            return selected;
        } catch (RuntimeException | Error e) {
            created.completeExceptionally(e);
            throw e;
        } finally {
            selectionInFlight.remove(cacheKey, created);
            if (properties.isCacheEnabled()) {
                maintainSelectionCache(true);
            }
        }
    }

    private List<SelectedFewShotCase> cachedSelection(
            SelectionCacheEntry cached,
            long startedAt,
            String source
    ) {
        recordMetrics(cached.selectionMode(), true, cached.selectedCases().size(), startedAt);
        log.info(
                "dynamic few-shot selection completed. enabled=true, selectionMode={}, cacheHit=true, cacheSource={}, selectedIds={}, sources={}, scores={}, datasetVersion={}, latencyMs={}",
                cached.selectionMode(),
                source,
                cached.selectedCases().stream().map(item -> item.fewShotCase().id()).toList(),
                cached.selectedCases().stream().map(item -> item.fewShotCase().source()).toList(),
                cached.selectedCases().stream().map(item -> "%.4f".formatted(item.score())).toList(),
                properties.getDatasetVersion(),
                (System.nanoTime() - startedAt) / 1_000_000
        );
        return cached.selectedCases();
    }

    private SelectionCacheEntry awaitSelection(CompletableFuture<SelectionCacheEntry> existing) {
        long timeoutMillis = properties.getSelectionInFlightWaitTimeout() == null
                ? DEFAULT_SELECTION_IN_FLIGHT_WAIT_TIMEOUT_MILLIS
                : Math.max(1L, properties.getSelectionInFlightWaitTimeout().toMillis());
        try {
            return existing.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("공유된 Few-shot selection 대기 중 인터럽트되었습니다.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("공유된 Few-shot selection 생성에 실패했습니다.", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("공유된 Few-shot selection 대기 시간이 초과되었습니다.", e);
        }
    }

    private void recordMetrics(
            FewShotSelectionMode selectionMode,
            boolean cacheHit,
            int selectedCount,
            long startedAt
    ) {
        metricsRecorder.recordSelection(
                selectionMode,
                cacheHit,
                selectedCount,
                (System.nanoTime() - startedAt) / 1_000_000
        );
    }

    private List<SelectedFewShotCase> selectWithCohere(
            FewShotSearchQuery query,
            List<FewShotCase> candidates,
            int topK
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            String queryText = textBuilder.buildQueryText(query);
            List<String> documents = candidates.stream()
                    .map(textBuilder::buildCandidateDocument)
                    .toList();
            float[] queryEmbedding = resolveQueryEmbedding(queryText);
            List<float[]> documentEmbeddings = resolveDocumentEmbeddings(candidates, documents);
            List<SelectedFewShotCase> ranked = new ArrayList<>();
            List<Double> similarityScores = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                double score = cosineSimilarity(queryEmbedding, documentEmbeddings.get(i));
                similarityScores.add(score);
                if (score >= properties.getSearch().getMinSimilarity()) {
                    ranked.add(new SelectedFewShotCase(candidates.get(i), score, "cohere-embedding"));
                }
            }
            logSimilarityDistribution(similarityScores, ranked.size());
            ranked.sort(Comparator
                    .comparingDouble(SelectedFewShotCase::score).reversed()
                    .thenComparingInt(item -> -item.fewShotCase().priority())
                    .thenComparing(item -> item.fewShotCase().id()));
            return diversify(ranked, topK);
        } catch (Exception e) {
            metricsRecorder.recordCohereFailure(e.getClass().getSimpleName());
            log.warn("dynamic few-shot Cohere selection failed. fallback=local, reason={}, message={}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("dynamic few-shot Cohere exception", e);
            return List.of();
        }
    }

    private void logSimilarityDistribution(List<Double> scores, int passedThresholdCount) {
        if (scores.isEmpty()) {
            return;
        }
        double topScore = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double bottomScore = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double avgScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        log.info(
                "few-shot embedding similarity distribution. candidateCount={}, passedThresholdCount={}, minSimilarity={}, topScore={}, bottomScore={}, avgScore={}",
                scores.size(),
                passedThresholdCount,
                formatScore(properties.getSearch().getMinSimilarity()),
                formatScore(topScore),
                formatScore(bottomScore),
                formatScore(avgScore)
        );
    }

    private static String formatScore(double score) {
        return String.format(Locale.ROOT, "%.4f", score);
    }

    private float[] resolveQueryEmbedding(String queryText) {
        if (!properties.isCacheEnabled()) {
            return embedQuery(queryText);
        }
        Instant now = Instant.now();
        maintainQueryEmbeddingCache(now);
        String key = sha256(queryText);
        QueryEmbeddingCacheEntry cached = readQueryEmbeddingCache(key, now);
        if (cached != null) {
            log.debug("few-shot query embedding cache hit.");
            return cached.embedding();
        }

        CompletableFuture<QueryEmbeddingCacheEntry> created = new CompletableFuture<>();
        CompletableFuture<QueryEmbeddingCacheEntry> existing = queryEmbeddingInFlight.putIfAbsent(key, created);
        if (existing != null) {
            log.debug("few-shot query embedding in-flight request reused.");
            return awaitQueryEmbedding(existing).embedding();
        }

        QueryEmbeddingCacheEntry cachedAfterClaim = readQueryEmbeddingCache(key, Instant.now(), false);
        if (cachedAfterClaim != null) {
            created.complete(cachedAfterClaim);
            queryEmbeddingInFlight.remove(key, created);
            log.debug("few-shot query embedding cache hit after in-flight claim.");
            return cachedAfterClaim.embedding();
        }

        try {
            QueryEmbeddingCacheEntry initialized = new QueryEmbeddingCacheEntry(
                    embedQuery(queryText),
                    expiresAt()
            );
            queryEmbeddingCache.put(key, initialized);
            maintainQueryEmbeddingCache(Instant.now());
            created.complete(initialized);
            log.debug("few-shot query embedding cache initialized.");
            return initialized.embedding();
        } catch (RuntimeException | Error e) {
            created.completeExceptionally(e);
            throw e;
        } finally {
            queryEmbeddingInFlight.remove(key, created);
        }
    }

    private QueryEmbeddingCacheEntry readQueryEmbeddingCache(String key, Instant now) {
        return readQueryEmbeddingCache(key, now, true);
    }

    private QueryEmbeddingCacheEntry readQueryEmbeddingCache(String key, Instant now, boolean recordEvent) {
        QueryEmbeddingCacheEntry cached = queryEmbeddingCache.get(key);
        if (cached == null) {
            if (recordEvent) {
                metricsRecorder.recordCacheEvent("query_embedding", "miss", 1L);
            }
            return null;
        }
        if (cached.expiresAt().isBefore(now)) {
            queryEmbeddingCache.remove(key, cached);
            if (recordEvent) {
                metricsRecorder.recordCacheEvent("query_embedding", "expired", 1L);
            }
            return null;
        }
        QueryEmbeddingCacheEntry accessed = cached.accessedAt(now);
        queryEmbeddingCache.replace(key, cached, accessed);
        if (recordEvent) {
            metricsRecorder.recordCacheEvent("query_embedding", "hit", 1L);
        }
        return accessed;
    }

    private QueryEmbeddingCacheEntry awaitQueryEmbedding(
            CompletableFuture<QueryEmbeddingCacheEntry> existing
    ) {
        long timeoutMillis = properties.getQueryEmbeddingInFlightWaitTimeout() == null
                ? DEFAULT_QUERY_EMBEDDING_IN_FLIGHT_WAIT_TIMEOUT_MILLIS
                : Math.max(1L, properties.getQueryEmbeddingInFlightWaitTimeout().toMillis());
        try {
            return existing.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Few-shot query embedding 대기 중 인터럽트되었습니다.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("공유된 Few-shot query embedding 생성에 실패했습니다.", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("공유된 Few-shot query embedding 대기 시간이 초과되었습니다.", e);
        }
    }

    private void maintainQueryEmbeddingCache(Instant now) {
        int maxSize = Math.max(1, properties.getQueryEmbeddingCacheMaxSize());
        long nowMillis = now.toEpochMilli();
        if (queryEmbeddingCache.size() < maxSize
                && nowMillis < queryEmbeddingCacheNextCleanupAt.get()) {
            return;
        }
        if (!queryEmbeddingCacheCleanupInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            int sizeBefore = queryEmbeddingCache.size();
            queryEmbeddingCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
            int sizeAfterExpiration = queryEmbeddingCache.size();
            metricsRecorder.recordCacheEvent("query_embedding", "expired", sizeBefore - sizeAfterExpiration);
            if (sizeAfterExpiration >= maxSize) {
                int trimTarget = Math.max(1, maxSize - Math.max(1, maxSize / 10));
                int removalCount = sizeAfterExpiration - trimTarget;
                queryEmbeddingCache.entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getValue().lastAccessedAt()))
                        .limit(removalCount)
                        .forEach(entry -> queryEmbeddingCache.remove(entry.getKey(), entry.getValue()));
            }
            int evictedCount = sizeAfterExpiration - queryEmbeddingCache.size();
            metricsRecorder.recordCacheEvent("query_embedding", "evicted", evictedCount);
            queryEmbeddingCacheNextCleanupAt.set(nowMillis + QUERY_EMBEDDING_CACHE_CLEANUP_INTERVAL_MILLIS);
            log.debug(
                    "few-shot query embedding cache maintained. sizeBefore={}, sizeAfter={}, maxSize={}",
                    sizeBefore,
                    queryEmbeddingCache.size(),
                    maxSize
            );
        } finally {
            queryEmbeddingCacheCleanupInProgress.set(false);
        }
    }

    private List<float[]> resolveDocumentEmbeddings(
            List<FewShotCase> candidates,
            List<String> documents
    ) {
        if (!properties.isCacheEnabled()) {
            return embedDocuments(documents);
        }
        Instant now = Instant.now();
        maintainDocumentEmbeddingCache(false);

        List<float[]> result = new ArrayList<>(java.util.Collections.nCopies(candidates.size(), null));
        List<PendingDocumentEmbedding> pending = new ArrayList<>();
        int cacheHitCount = 0;
        int inFlightReuseCount = 0;

        for (int i = 0; i < candidates.size(); i++) {
            String key = documentEmbeddingCacheKey(candidates.get(i), documents.get(i));
            DocumentEmbeddingCacheEntry cached = documentEmbeddingCache.get(key);
            if (cached != null && cached.expiresAt().isBefore(now)) {
                if (documentEmbeddingCache.remove(key, cached)) {
                    metricsRecorder.recordCacheEvent("document_embedding", "expired", 1L);
                }
                cached = null;
            }
            if (cached != null) {
                DocumentEmbeddingCacheEntry accessed = cached.accessedAt(now);
                documentEmbeddingCache.replace(key, cached, accessed);
                result.set(i, accessed.embedding());
                cacheHitCount++;
                metricsRecorder.recordCacheEvent("document_embedding", "hit", 1L);
                continue;
            }
            metricsRecorder.recordCacheEvent("document_embedding", "miss", 1L);

            CompletableFuture<DocumentEmbeddingCacheEntry> created = new CompletableFuture<>();
            CompletableFuture<DocumentEmbeddingCacheEntry> existing = documentEmbeddingInFlight.putIfAbsent(key, created);
            boolean owner = existing == null;
            if (owner) {
                DocumentEmbeddingCacheEntry cachedAfterClaim = documentEmbeddingCache.get(key);
                if (cachedAfterClaim != null && cachedAfterClaim.expiresAt().isBefore(Instant.now())) {
                    if (documentEmbeddingCache.remove(key, cachedAfterClaim)) {
                        metricsRecorder.recordCacheEvent("document_embedding", "expired", 1L);
                    }
                    cachedAfterClaim = null;
                }
                if (cachedAfterClaim != null) {
                    created.complete(cachedAfterClaim);
                    documentEmbeddingInFlight.remove(key, created);
                    result.set(i, cachedAfterClaim.embedding());
                    cacheHitCount++;
                    metricsRecorder.recordCacheEvent("document_embedding", "hit_after_claim", 1L);
                    continue;
                }
            }
            if (!owner) {
                inFlightReuseCount++;
            }
            pending.add(new PendingDocumentEmbedding(
                    i,
                    key,
                    documents.get(i),
                    owner ? created : existing,
                    owner
            ));
        }

        initializeMissingDocumentEmbeddings(pending);
        for (PendingDocumentEmbedding item : pending) {
            result.set(item.index(), item.future().join().embedding());
        }
        log.debug(
                "few-shot document embedding cache resolved. hitCount={}, initializedCount={}, inFlightReuseCount={}, candidateCount={}, datasetVersion={}",
                cacheHitCount,
                pending.stream().filter(PendingDocumentEmbedding::owner).count(),
                inFlightReuseCount,
                candidates.size(),
                properties.getDatasetVersion()
        );
        return List.copyOf(result);
    }

    private void maintainDocumentEmbeddingCache(boolean requestFollowUpIfBusy) {
        int maxSize = Math.max(1, properties.getDocumentEmbeddingCacheMaxSize());
        if (!documentEmbeddingCacheCleanupInProgress.compareAndSet(false, true)) {
            if (requestFollowUpIfBusy) {
                documentEmbeddingCacheCleanupRequested.set(true);
            }
            return;
        }
        try {
            do {
                documentEmbeddingCacheCleanupRequested.set(false);
                Instant cleanupTime = Instant.now();
                int sizeBefore = documentEmbeddingCache.size();
                documentEmbeddingCache.entrySet().removeIf(entry ->
                        entry.getValue().expiresAt().isBefore(cleanupTime)
                                && !documentEmbeddingInFlight.containsKey(entry.getKey()));
                int sizeAfterExpiration = documentEmbeddingCache.size();
                metricsRecorder.recordCacheEvent("document_embedding", "expired", sizeBefore - sizeAfterExpiration);
                if (sizeAfterExpiration > maxSize) {
                    int removalCount = sizeAfterExpiration - maxSize;
                    documentEmbeddingCache.entrySet().stream()
                            .filter(entry -> !documentEmbeddingInFlight.containsKey(entry.getKey()))
                            .sorted(Comparator.comparing(entry -> entry.getValue().lastAccessedAt()))
                            .limit(removalCount)
                            .forEach(entry -> documentEmbeddingCache.remove(entry.getKey(), entry.getValue()));
                }
                metricsRecorder.recordCacheEvent(
                        "document_embedding", "evicted", sizeAfterExpiration - documentEmbeddingCache.size());
            } while (documentEmbeddingCacheCleanupRequested.getAndSet(false));
        } finally {
            documentEmbeddingCacheCleanupInProgress.set(false);
            if (documentEmbeddingCacheCleanupRequested.getAndSet(false)) {
                maintainDocumentEmbeddingCache(true);
            }
        }
    }

    private void initializeMissingDocumentEmbeddings(List<PendingDocumentEmbedding> pending) {
        List<PendingDocumentEmbedding> owned = pending.stream()
                .filter(PendingDocumentEmbedding::owner)
                .toList();
        if (owned.isEmpty()) {
            return;
        }
        try {
            List<float[]> embeddedDocuments = embedDocuments(
                    owned.stream().map(PendingDocumentEmbedding::document).toList()
            );
            if (embeddedDocuments.size() != owned.size()) {
                throw new IllegalStateException("Cohere document embedding count does not match candidate count.");
            }
            Instant expiresAt = expiresAt();
            for (int i = 0; i < owned.size(); i++) {
                PendingDocumentEmbedding item = owned.get(i);
                DocumentEmbeddingCacheEntry entry = new DocumentEmbeddingCacheEntry(
                        embeddedDocuments.get(i),
                        expiresAt
                );
                documentEmbeddingCache.put(item.key(), entry);
                item.future().complete(entry);
            }
        } catch (RuntimeException | Error e) {
            owned.forEach(item -> item.future().completeExceptionally(e));
            throw e;
        } finally {
            owned.forEach(item -> documentEmbeddingInFlight.remove(item.key(), item.future()));
            maintainDocumentEmbeddingCache(true);
        }
    }

    private List<FewShotCase> localPrefilter(List<FewShotCase> activeCases, FewShotSearchQuery query) {
        int limit = Math.max(1, properties.getSearch().getCandidateLimit());
        String queryInputHash = FewShotInputHash.of(
                query.mainTasks(),
                query.qualifications(),
                query.question(),
                query.answer()
        );
        return activeCases.stream()
                .filter(fewShotCase -> !sameCase(query.caseId(), fewShotCase.id()))
                .filter(fewShotCase -> !sameNormalizedInput(queryInputHash, fewShotCase))
                .map(fewShotCase -> new LocalScore(fewShotCase, localScore(query, fewShotCase)))
                .sorted(Comparator
                        .comparingDouble(LocalScore::score).reversed()
                        .thenComparingInt(item -> -item.fewShotCase().priority())
                        .thenComparing(item -> item.fewShotCase().id()))
                .limit(limit)
                .map(LocalScore::fewShotCase)
                .toList();
    }

    private List<SelectedFewShotCase> selectLocally(
            FewShotSearchQuery query,
            List<FewShotCase> candidates,
            int topK,
            String method
    ) {
        List<SelectedFewShotCase> ranked = candidates.stream()
                .map(fewShotCase -> new SelectedFewShotCase(fewShotCase, localScore(query, fewShotCase), method))
                .sorted(Comparator
                        .comparingDouble(SelectedFewShotCase::score).reversed()
                        .thenComparingInt(item -> -item.fewShotCase().priority())
                        .thenComparing(item -> item.fewShotCase().id()))
                .toList();
        return diversify(ranked, topK);
    }

    private List<SelectedFewShotCase> diversify(List<SelectedFewShotCase> ranked, int topK) {
        if (!properties.getSearch().isDiversityEnabled() || ranked.size() <= topK) {
            return ranked.stream().limit(topK).toList();
        }
        Map<FewShotSource, SelectedFewShotCase> bySource = new LinkedHashMap<>();
        List<SelectedFewShotCase> result = new ArrayList<>();
        for (SelectedFewShotCase selected : ranked) {
            if (bySource.putIfAbsent(selected.fewShotCase().source(), selected) == null) {
                result.add(selected);
                if (result.size() == topK) {
                    return List.copyOf(result);
                }
            }
        }
        for (SelectedFewShotCase selected : ranked) {
            if (!result.contains(selected)) {
                result.add(selected);
                if (result.size() == topK) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private double localScore(FewShotSearchQuery query, FewShotCase fewShotCase) {
        Set<String> queryTokens = tokens(textBuilder.buildQueryText(query));
        Set<String> candidateTokens = tokens(textBuilder.buildCandidateDocument(fewShotCase));
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) {
            return fewShotCase.priority() / 1000.0;
        }
        long overlap = queryTokens.stream().filter(candidateTokens::contains).count();
        double jaccard = overlap / (double) (queryTokens.size() + candidateTokens.size() - overlap);
        return jaccard + fewShotCase.priority() / 1000.0;
    }

    private SelectionCacheEntry readSelectionCache(String key) {
        return readSelectionCache(key, true);
    }

    private SelectionCacheEntry readSelectionCache(String key, boolean recordEvent) {
        if (!properties.isCacheEnabled()) {
            return null;
        }
        Instant now = Instant.now();
        maintainSelectionCache(false);
        SelectionCacheEntry cached = selectionCache.get(key);
        if (cached == null) {
            if (recordEvent) {
                metricsRecorder.recordCacheEvent("selection", "miss", 1L);
            }
            return null;
        }
        if (cached.expiresAt().isBefore(now)) {
            selectionCache.remove(key, cached);
            if (recordEvent) {
                metricsRecorder.recordCacheEvent("selection", "expired", 1L);
            }
            return null;
        }
        SelectionCacheEntry accessed = cached.accessedAt(now);
        selectionCache.replace(key, cached, accessed);
        if (recordEvent) {
            metricsRecorder.recordCacheEvent("selection", "hit", 1L);
        }
        return accessed;
    }

    private void maintainSelectionCache(boolean requestFollowUpIfBusy) {
        int maxSize = Math.max(1, properties.getSelectionCacheMaxSize());
        if (!selectionCacheCleanupInProgress.compareAndSet(false, true)) {
            if (requestFollowUpIfBusy) {
                selectionCacheCleanupRequested.set(true);
            }
            return;
        }
        try {
            do {
                selectionCacheCleanupRequested.set(false);
                Instant cleanupTime = Instant.now();
                int sizeBefore = selectionCache.size();
                selectionCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(cleanupTime));
                int sizeAfterExpiration = selectionCache.size();
                metricsRecorder.recordCacheEvent("selection", "expired", sizeBefore - sizeAfterExpiration);
                if (sizeAfterExpiration > maxSize) {
                    int removalCount = sizeAfterExpiration - maxSize;
                    selectionCache.entrySet().stream()
                            .filter(entry -> !selectionInFlight.containsKey(entry.getKey()))
                            .sorted(Comparator.comparing(entry -> entry.getValue().lastAccessedAt()))
                            .limit(removalCount)
                            .forEach(entry -> selectionCache.remove(entry.getKey(), entry.getValue()));
                }
                metricsRecorder.recordCacheEvent("selection", "evicted", sizeAfterExpiration - selectionCache.size());
            } while (selectionCacheCleanupRequested.getAndSet(false));
        } finally {
            selectionCacheCleanupInProgress.set(false);
            if (selectionCacheCleanupRequested.getAndSet(false)) {
                maintainSelectionCache(true);
            }
        }
    }

    private String selectionCacheKey(FewShotSearchQuery query, int topK, String datasetFingerprint) {
        return sha256(
                datasetFingerprint
                        + "\n" + topK
                        + "\n" + properties.getSearch().getMinSimilarity()
                        + "\n" + properties.getSearch().getMinimumSelectedCount()
                        + "\n" + properties.isFallbackEnabled()
                        + "\n" + properties.getSearch().isDiversityEnabled()
                        + "\n" + defaultString(query.caseId())
                        + "\n" + textBuilder.buildQueryText(query)
        );
    }

    private String datasetFingerprint(List<FewShotCase> activeCases) {
        StringBuilder source = new StringBuilder(properties.getDatasetVersion());
        for (FewShotCase fewShotCase : activeCases) {
            source.append('\n')
                    .append(defaultString(fewShotCase.id())).append('\u001f')
                    .append(fewShotCase.source()).append('\u001f')
                    .append(fewShotCase.priority()).append('\u001f')
                    .append(textBuilder.buildCandidateDocument(fewShotCase)).append('\u001f')
                    .append(defaultString(fewShotCase.promptBlock()));
        }
        return sha256(source.toString());
    }

    private String documentEmbeddingCacheKey(FewShotCase fewShotCase, String document) {
        return sha256(
                properties.getDatasetVersion()
                        + "\n" + defaultString(fewShotCase.id())
                        + "\n" + document
        );
    }

    private Instant expiresAt() {
        return Instant.now().plus(properties.getCacheTtl());
    }

    private static boolean sameCase(String queryCaseId, String candidateId) {
        return StringUtils.hasText(queryCaseId) && queryCaseId.equals(candidateId);
    }

    private static boolean sameNormalizedInput(String queryInputHash, FewShotCase fewShotCase) {
        if (!StringUtils.hasText(queryInputHash)) {
            return false;
        }
        String candidateInputHash = FewShotInputHash.of(
                fewShotCase.mainTasks(),
                fewShotCase.qualifications(),
                fewShotCase.question(),
                fewShotCase.sanitizedAnswer()
        );
        return queryInputHash.equals(candidateInputHash);
    }


    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static Set<String> tokens(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT_PATTERN.split(text.toLowerCase(Locale.ROOT))) {
            if (token.length() >= 2) {
                result.add(token);
            }
        }
        return result;
    }

    private static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append("%02x".formatted(b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private record LocalScore(FewShotCase fewShotCase, double score) {
    }

    private record PendingDocumentEmbedding(
            int index,
            String key,
            String document,
            CompletableFuture<DocumentEmbeddingCacheEntry> future,
            boolean owner
    ) {
    }

    private record SelectionCacheEntry(
            List<SelectedFewShotCase> selectedCases,
            FewShotSelectionMode selectionMode,
            Instant expiresAt,
            Instant lastAccessedAt
    ) {
        private SelectionCacheEntry(
                List<SelectedFewShotCase> selectedCases,
                FewShotSelectionMode selectionMode,
                Instant expiresAt
        ) {
            this(selectedCases, selectionMode, expiresAt, Instant.now());
        }

        private SelectionCacheEntry {
            selectedCases = selectedCases == null ? List.of() : List.copyOf(selectedCases);
        }

        private SelectionCacheEntry accessedAt(Instant accessedAt) {
            return new SelectionCacheEntry(selectedCases, selectionMode, expiresAt, accessedAt);
        }
    }

    private record DocumentEmbeddingCacheEntry(float[] embedding, Instant expiresAt, Instant lastAccessedAt) {
        private DocumentEmbeddingCacheEntry(float[] embedding, Instant expiresAt) {
            this(embedding, expiresAt, Instant.now());
        }

        private DocumentEmbeddingCacheEntry {
            embedding = embedding == null ? new float[0] : embedding.clone();
        }

        private DocumentEmbeddingCacheEntry accessedAt(Instant accessedAt) {
            return new DocumentEmbeddingCacheEntry(embedding, expiresAt, accessedAt);
        }

        @Override
        public float[] embedding() {
            return embedding.clone();
        }
    }

    private record QueryEmbeddingCacheEntry(float[] embedding, Instant expiresAt, Instant lastAccessedAt) {
        private QueryEmbeddingCacheEntry(float[] embedding, Instant expiresAt) {
            this(embedding, expiresAt, Instant.now());
        }

        private QueryEmbeddingCacheEntry {
            embedding = embedding == null ? new float[0] : embedding.clone();
        }

        private QueryEmbeddingCacheEntry accessedAt(Instant accessedAt) {
            return new QueryEmbeddingCacheEntry(embedding, expiresAt, accessedAt);
        }

        @Override
        public float[] embedding() {
            return embedding.clone();
        }
    }

    @Override
    public long cohereApiCallCount() {
        return cohereEmbeddingClient.apiCallCount();
    }

    private float[] embedQuery(String queryText) {
        metricsRecorder.recordCohereLogicalCalls(1L);
        return cohereEmbeddingClient.embedQuery(queryText);
    }

    private List<float[]> embedDocuments(List<String> documents) {
        metricsRecorder.recordCohereLogicalCalls(1L);
        return cohereEmbeddingClient.embedDocuments(documents);
    }

    int selectionCacheSize() {
        return selectionCache.size();
    }

    int queryEmbeddingCacheSize() {
        return queryEmbeddingCache.size();
    }

    int documentEmbeddingCacheSize() {
        return documentEmbeddingCache.size();
    }
}

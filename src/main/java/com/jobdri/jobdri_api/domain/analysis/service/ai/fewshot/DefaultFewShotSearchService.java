package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
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
import java.util.regex.Pattern;

@Service
@Slf4j
public class DefaultFewShotSearchService implements FewShotSearchService {
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+");
    private static final Pattern NORMALIZED_INPUT_WHITESPACE_PATTERN = Pattern.compile("[\\p{Z}\\s]+");

    private final FewShotCaseStore caseStore;
    private final FewShotSearchTextBuilder textBuilder;
    private final CohereEmbeddingClient cohereEmbeddingClient;
    private final FewShotProperties properties;
    private final Map<String, SelectionCacheEntry> selectionCache = new ConcurrentHashMap<>();
    private final Map<String, DocumentEmbeddingCacheEntry> documentEmbeddingCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<DocumentEmbeddingCacheEntry>> documentEmbeddingInFlight =
            new ConcurrentHashMap<>();

    public DefaultFewShotSearchService(
            FewShotCaseStore caseStore,
            FewShotSearchTextBuilder textBuilder,
            CohereEmbeddingClient cohereEmbeddingClient,
            FewShotProperties properties
    ) {
        this.caseStore = caseStore;
        this.textBuilder = textBuilder;
        this.cohereEmbeddingClient = cohereEmbeddingClient;
        this.properties = properties;
    }

    @Override
    public List<SelectedFewShotCase> searchRelevantFewShots(FewShotSearchQuery query, int topK) {
        if (!properties.isDynamicSelectionEnabled()) {
            log.debug("dynamic few-shot selection disabled.");
            return List.of();
        }
        int requestedTopK = topK > 0 ? topK : properties.getSearch().getTopK();
        List<FewShotCase> activeCases = caseStore.loadActiveCases();
        String datasetFingerprint = datasetFingerprint(activeCases);
        String cacheKey = selectionCacheKey(query, requestedTopK, datasetFingerprint);
        SelectionCacheEntry cached = readSelectionCache(cacheKey);
        if (cached != null) {
            log.debug(
                    "few-shot selection cache hit. selectionMode={}, selectedCount={}, datasetVersion={}",
                    cached.selectionMode(),
                    cached.selectedCases().size(),
                    properties.getDatasetVersion()
            );
            return cached.selectedCases();
        }

        long startedAt = System.nanoTime();
        List<FewShotCase> candidates = localPrefilter(activeCases, query);
        List<SelectedFewShotCase> selected = selectWithCohere(query, candidates, requestedTopK);
        FewShotSelectionMode selectionMode = selected.isEmpty()
                ? FewShotSelectionMode.STATIC_FALLBACK
                : FewShotSelectionMode.EMBEDDING;
        if (selected.isEmpty() && properties.isFallbackEnabled()) {
            selected = selectLocally(query, candidates, requestedTopK, "local-fallback");
            if (!selected.isEmpty()) {
                selectionMode = FewShotSelectionMode.LOCAL_FALLBACK;
            }
        }
        if (properties.isCacheEnabled()) {
            selectionCache.put(cacheKey, new SelectionCacheEntry(selected, selectionMode, expiresAt()));
        }
        log.info(
                "dynamic few-shot selection completed. enabled=true, selectionMode={}, totalCandidates={}, filteredCandidates={}, selectedIds={}, sources={}, scores={}, latencyMs={}",
                selectionMode,
                activeCases.size(),
                candidates.size(),
                selected.stream().map(item -> item.fewShotCase().id()).toList(),
                selected.stream().map(item -> item.fewShotCase().source()).toList(),
                selected.stream().map(item -> "%.4f".formatted(item.score())).toList(),
                (System.nanoTime() - startedAt) / 1_000_000
        );
        return selected;
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
            float[] queryEmbedding = cohereEmbeddingClient.embedQuery(queryText);
            List<float[]> documentEmbeddings = resolveDocumentEmbeddings(candidates, documents);
            List<SelectedFewShotCase> ranked = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                double score = cosineSimilarity(queryEmbedding, documentEmbeddings.get(i));
                if (score >= properties.getSearch().getMinRerankScore()) {
                    ranked.add(new SelectedFewShotCase(candidates.get(i), score, "cohere-embedding"));
                }
            }
            ranked.sort(Comparator
                    .comparingDouble(SelectedFewShotCase::score).reversed()
                    .thenComparingInt(item -> -item.fewShotCase().priority())
                    .thenComparing(item -> item.fewShotCase().id()));
            return diversify(ranked, topK);
        } catch (Exception e) {
            log.warn("dynamic few-shot Cohere selection failed. fallback=local, reason={}, message={}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("dynamic few-shot Cohere exception", e);
            return List.of();
        }
    }

    private List<float[]> resolveDocumentEmbeddings(
            List<FewShotCase> candidates,
            List<String> documents
    ) {
        if (!properties.isCacheEnabled()) {
            return cohereEmbeddingClient.embedDocuments(documents);
        }
        Instant now = Instant.now();
        documentEmbeddingCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));

        List<float[]> result = new ArrayList<>(java.util.Collections.nCopies(candidates.size(), null));
        List<PendingDocumentEmbedding> pending = new ArrayList<>();
        int cacheHitCount = 0;
        int inFlightReuseCount = 0;

        for (int i = 0; i < candidates.size(); i++) {
            String key = documentEmbeddingCacheKey(candidates.get(i), documents.get(i));
            DocumentEmbeddingCacheEntry cached = documentEmbeddingCache.get(key);
            if (cached != null) {
                result.set(i, cached.embedding());
                cacheHitCount++;
                continue;
            }

            CompletableFuture<DocumentEmbeddingCacheEntry> created = new CompletableFuture<>();
            CompletableFuture<DocumentEmbeddingCacheEntry> existing = documentEmbeddingInFlight.putIfAbsent(key, created);
            boolean owner = existing == null;
            if (owner) {
                DocumentEmbeddingCacheEntry cachedAfterClaim = documentEmbeddingCache.get(key);
                if (cachedAfterClaim != null) {
                    created.complete(cachedAfterClaim);
                    documentEmbeddingInFlight.remove(key, created);
                    result.set(i, cachedAfterClaim.embedding());
                    cacheHitCount++;
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

    private void initializeMissingDocumentEmbeddings(List<PendingDocumentEmbedding> pending) {
        List<PendingDocumentEmbedding> owned = pending.stream()
                .filter(PendingDocumentEmbedding::owner)
                .toList();
        if (owned.isEmpty()) {
            return;
        }
        try {
            List<float[]> embeddedDocuments = cohereEmbeddingClient.embedDocuments(
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
        }
    }

    private List<FewShotCase> localPrefilter(List<FewShotCase> activeCases, FewShotSearchQuery query) {
        int limit = Math.max(1, properties.getSearch().getCandidateLimit());
        String queryInputHash = normalizedInputHash(
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
        if (!properties.isCacheEnabled()) {
            return null;
        }
        Instant now = Instant.now();
        selectionCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        return selectionCache.get(key);
    }

    private String selectionCacheKey(FewShotSearchQuery query, int topK, String datasetFingerprint) {
        return sha256(
                datasetFingerprint
                        + "\n" + topK
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
        String candidateInputHash = normalizedInputHash(
                fewShotCase.mainTasks(),
                fewShotCase.qualifications(),
                fewShotCase.question(),
                fewShotCase.sanitizedAnswer()
        );
        return queryInputHash.equals(candidateInputHash);
    }

    private static String normalizedInputHash(
            List<String> mainTasks,
            List<String> qualifications,
            String question,
            String answer
    ) {
        String normalizedMainTasks = normalizeInputSection(mainTasks == null ? "" : String.join("\n", mainTasks));
        String normalizedQualifications = normalizeInputSection(
                qualifications == null ? "" : String.join("\n", qualifications)
        );
        String normalizedQuestion = normalizeInputSection(question);
        String normalizedAnswer = normalizeInputSection(answer);
        if (normalizedMainTasks.isEmpty()
                && normalizedQualifications.isEmpty()
                && normalizedQuestion.isEmpty()
                && normalizedAnswer.isEmpty()) {
            return "";
        }
        return sha256(
                normalizedMainTasks + '\u001f'
                        + normalizedQualifications + '\u001f'
                        + normalizedQuestion + '\u001f'
                        + normalizedAnswer
        );
    }

    private static String normalizeInputSection(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String unicodeNormalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return NORMALIZED_INPUT_WHITESPACE_PATTERN.matcher(unicodeNormalized)
                .replaceAll(" ")
                .trim()
                .toLowerCase(Locale.ROOT);
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
            Instant expiresAt
    ) {
        private SelectionCacheEntry {
            selectedCases = selectedCases == null ? List.of() : List.copyOf(selectedCases);
        }
    }

    private record DocumentEmbeddingCacheEntry(float[] embedding, Instant expiresAt) {
        private DocumentEmbeddingCacheEntry {
            embedding = embedding == null ? new float[0] : embedding.clone();
        }

        @Override
        public float[] embedding() {
            return embedding.clone();
        }
    }
}

package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DefaultFewShotSearchService implements FewShotSearchService {
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+");

    private final FewShotCaseStore caseStore;
    private final FewShotSearchTextBuilder textBuilder;
    private final CohereEmbeddingClient cohereEmbeddingClient;
    private final FewShotProperties properties;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

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
        String cacheKey = cacheKey(query, requestedTopK);
        CacheEntry cached = readCache(cacheKey);
        if (cached != null) {
            log.debug("few-shot selection cache hit. selectedCount={}, datasetVersion={}", cached.selectedCases().size(), properties.getDatasetVersion());
            return cached.selectedCases();
        }

        long startedAt = System.nanoTime();
        List<FewShotCase> activeCases = caseStore.loadActiveCases();
        List<FewShotCase> candidates = localPrefilter(activeCases, query);
        List<SelectedFewShotCase> selected = selectWithCohere(query, candidates, requestedTopK);
        if (selected.isEmpty() && properties.isFallbackEnabled()) {
            selected = selectLocally(query, candidates, requestedTopK, "local-fallback");
        }
        if (properties.isCacheEnabled()) {
            cache.put(cacheKey, new CacheEntry(selected, Instant.now().plus(properties.getCacheTtl())));
        }
        log.info(
                "dynamic few-shot selection completed. enabled=true, totalCandidates={}, filteredCandidates={}, selectedIds={}, sources={}, scores={}, latencyMs={}",
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
            List<float[]> documentEmbeddings = cohereEmbeddingClient.embedDocuments(documents);
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

    private List<FewShotCase> localPrefilter(List<FewShotCase> activeCases, FewShotSearchQuery query) {
        int limit = Math.max(1, properties.getSearch().getCandidateLimit());
        return activeCases.stream()
                .filter(fewShotCase -> !sameCase(query.caseId(), fewShotCase.id()))
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

    private CacheEntry readCache(String key) {
        if (!properties.isCacheEnabled()) {
            return null;
        }
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    private String cacheKey(FewShotSearchQuery query, int topK) {
        return sha256(properties.getDatasetVersion() + "\n" + topK + "\n" + textBuilder.buildQueryText(query));
    }

    private static boolean sameCase(String queryCaseId, String candidateId) {
        return StringUtils.hasText(queryCaseId) && queryCaseId.equals(candidateId);
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

    private record CacheEntry(List<SelectedFewShotCase> selectedCases, Instant expiresAt) {
        private CacheEntry {
            selectedCases = selectedCases == null ? List.of() : List.copyOf(selectedCases);
        }
    }
}

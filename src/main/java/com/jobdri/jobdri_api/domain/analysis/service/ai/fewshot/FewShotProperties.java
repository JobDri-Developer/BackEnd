package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "analysis.few-shot")
public class FewShotProperties {
    private boolean dynamicSelectionEnabled = false;
    private String datasetVersion = "fewshot-static-v1";
    private String curatedResource = "analysis/fewshot/curated-fewshot-cases.json";
    private String reviewedEvaluationResource = "analysis/fewshot/reviewed-fewshot-cases.json";
    private String reviewedEvaluationCsvPath = "";
    private boolean fallbackEnabled = true;
    private boolean cacheEnabled = true;
    private Duration cacheTtl = Duration.ofMinutes(30);
    private Source source = new Source();
    private Search search = new Search();

    public boolean isDynamicSelectionEnabled() {
        return dynamicSelectionEnabled;
    }

    public void setDynamicSelectionEnabled(boolean dynamicSelectionEnabled) {
        this.dynamicSelectionEnabled = dynamicSelectionEnabled;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(String datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public String getCuratedResource() {
        return curatedResource;
    }

    public void setCuratedResource(String curatedResource) {
        this.curatedResource = curatedResource;
    }

    public String getReviewedEvaluationResource() {
        return reviewedEvaluationResource;
    }

    public void setReviewedEvaluationResource(String reviewedEvaluationResource) {
        this.reviewedEvaluationResource = reviewedEvaluationResource;
    }

    public String getReviewedEvaluationCsvPath() {
        return reviewedEvaluationCsvPath;
    }

    public void setReviewedEvaluationCsvPath(String reviewedEvaluationCsvPath) {
        this.reviewedEvaluationCsvPath = reviewedEvaluationCsvPath;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source == null ? new Source() : source;
    }

    public Search getSearch() {
        return search;
    }

    public void setSearch(Search search) {
        this.search = search == null ? new Search() : search;
    }

    public static class Source {
        private boolean fixedEnabled = true;
        private boolean curatedEnabled = true;
        private boolean reviewedEvaluationEnabled = false;
        private boolean reviewedProductionEnabled = false;

        public boolean isFixedEnabled() {
            return fixedEnabled;
        }

        public void setFixedEnabled(boolean fixedEnabled) {
            this.fixedEnabled = fixedEnabled;
        }

        public boolean isCuratedEnabled() {
            return curatedEnabled;
        }

        public void setCuratedEnabled(boolean curatedEnabled) {
            this.curatedEnabled = curatedEnabled;
        }

        public boolean isReviewedEvaluationEnabled() {
            return reviewedEvaluationEnabled;
        }

        public void setReviewedEvaluationEnabled(boolean reviewedEvaluationEnabled) {
            this.reviewedEvaluationEnabled = reviewedEvaluationEnabled;
        }

        public boolean isReviewedProductionEnabled() {
            return reviewedProductionEnabled;
        }

        public void setReviewedProductionEnabled(boolean reviewedProductionEnabled) {
            this.reviewedProductionEnabled = reviewedProductionEnabled;
        }
    }

    public static class Search {
        private int candidateLimit = 30;
        private int topK = 5;
        private double minRerankScore = -1.0;
        private boolean diversityEnabled = true;

        public int getCandidateLimit() {
            return candidateLimit;
        }

        public void setCandidateLimit(int candidateLimit) {
            this.candidateLimit = candidateLimit;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinRerankScore() {
            return minRerankScore;
        }

        public void setMinRerankScore(double minRerankScore) {
            this.minRerankScore = minRerankScore;
        }

        public boolean isDiversityEnabled() {
            return diversityEnabled;
        }

        public void setDiversityEnabled(boolean diversityEnabled) {
            this.diversityEnabled = diversityEnabled;
        }
    }
}

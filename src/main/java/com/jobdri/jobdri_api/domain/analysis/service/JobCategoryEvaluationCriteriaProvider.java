package com.jobdri.jobdri_api.domain.analysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class JobCategoryEvaluationCriteriaProvider {
    private static final String RESOURCE_PATH = "analysis/job-category-evaluation-criteria.json";
    private static final TypeReference<List<JobCategoryEvaluationCriteria>> CRITERIA_TYPE = new TypeReference<>() {
    };

    private final Map<String, JobCategoryEvaluationCriteria> criteriaByMiddleName;

    public JobCategoryEvaluationCriteriaProvider(ObjectMapper objectMapper) {
        this.criteriaByMiddleName = loadCriteria(objectMapper);
        log.info("Loaded job category evaluation criteria. count={}", criteriaByMiddleName.size());
    }

    public Optional<JobCategoryEvaluationCriteria> findByMiddleName(String middleName) {
        if (!StringUtils.hasText(middleName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(criteriaByMiddleName.get(normalizeKey(middleName)));
    }

    private Map<String, JobCategoryEvaluationCriteria> loadCriteria(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            List<JobCategoryEvaluationCriteria> criteria = objectMapper.readValue(inputStream, CRITERIA_TYPE);
            Map<String, JobCategoryEvaluationCriteria> result = new LinkedHashMap<>();

            for (JobCategoryEvaluationCriteria item : criteria) {
                if (item == null || !StringUtils.hasText(item.jobCategoryMiddle())) {
                    log.warn("Skipped invalid job category evaluation criteria row. reason=blank_middle_name");
                    continue;
                }

                String key = normalizeKey(item.jobCategoryMiddle());
                if (result.containsKey(key)) {
                    throw new IllegalStateException("중복된 직무 중분류 평가 기준입니다. middleName=" + item.jobCategoryMiddle());
                }
                result.put(key, item);
            }

            return Map.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException("직무 중분류 평가 기준 resource 로딩에 실패했습니다. path=" + RESOURCE_PATH, e);
        }
    }

    private String normalizeKey(String value) {
        return value.trim()
                .replace('ㆍ', '·')
                .replace('･', '·')
                .replace('/', '·')
                .replaceAll("\\s+", "");
    }
}

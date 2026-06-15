package com.jobdri.jobdri_api.domain.corpus.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.corpus.entity.CorpusClassificationMapping;
import com.jobdri.jobdri_api.domain.corpus.repository.CorpusClassificationMappingRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorpusClassificationResolver {

    private final CorpusClassificationMappingRepository mappingRepository;
    private final DetailClassificationRepository detailClassificationRepository;

    public Optional<DetailClassification> resolve(
            String jobGroupL1,
            String jobFamilyL2,
            String roleL3
    ) {
        String normalizedJobGroup = normalize(jobGroupL1);
        String normalizedJobFamily = normalize(jobFamilyL2);
        String normalizedRole = normalize(roleL3);

        if (!StringUtils.hasText(normalizedRole)) {
            return Optional.empty();
        }

        Optional<DetailClassification> mapped = mappingRepository
                .findBySourceJobGroupL1AndSourceJobFamilyL2AndSourceRoleL3(
                        normalizedJobGroup,
                        normalizedJobFamily,
                        normalizedRole
                )
                .map(CorpusClassificationMapping::getDetailClassification);
        if (mapped.isPresent()) {
            return mapped;
        }

        if (StringUtils.hasText(normalizedJobGroup) && StringUtils.hasText(normalizedJobFamily)) {
            Optional<DetailClassification> exactHierarchy = detailClassificationRepository.findByHierarchyNames(
                    normalizedJobGroup,
                    normalizedJobFamily,
                    normalizedRole
            );
            if (exactHierarchy.isPresent()) {
                return exactHierarchy;
            }
        }

        if (detailClassificationRepository.countByDetailNameIgnoreCase(normalizedRole) == 1) {
            return detailClassificationRepository.findByDetailNameIgnoreCase(normalizedRole);
        }

        return Optional.empty();
    }

    @Transactional
    public CorpusClassificationMapping registerMapping(
            String jobGroupL1,
            String jobFamilyL2,
            String roleL3,
            DetailClassification detailClassification
    ) {
        String normalizedJobGroup = normalize(jobGroupL1);
        String normalizedJobFamily = normalize(jobFamilyL2);
        String normalizedRole = normalize(roleL3);

        if (!StringUtils.hasText(normalizedJobGroup)
                || !StringUtils.hasText(normalizedJobFamily)
                || !StringUtils.hasText(normalizedRole)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "분류 매핑을 등록하려면 대분류, 중분류, 소분류가 모두 필요합니다."
            );
        }

        return mappingRepository
                .findBySourceJobGroupL1AndSourceJobFamilyL2AndSourceRoleL3(
                        normalizedJobGroup,
                        normalizedJobFamily,
                        normalizedRole
                )
                .orElseGet(() -> mappingRepository.save(CorpusClassificationMapping.create(
                        normalizedJobGroup,
                        normalizedJobFamily,
                        normalizedRole,
                        detailClassification
                )));
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

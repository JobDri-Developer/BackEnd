package com.jobdri.jobdri_api.domain.classification.service;

import com.jobdri.jobdri_api.domain.classification.dto.response.ClassificationLowerResponseDto;
import com.jobdri.jobdri_api.domain.classification.dto.response.ClassificationResponseDto;
import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.classification.repository.MiddleClassificationRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassificationService {

    private final ClassificationRepository classificationRepository;
    private final DetailClassificationRepository detailClassificationRepository;
    private final MiddleClassificationRepository middleClassificationRepository;

    public List<ClassificationResponseDto> getBigClassifications() {
        return classificationRepository.findAll().stream()
                .map(classification -> new ClassificationResponseDto(
                        classification.getId(),
                        classification.getBigName()
                ))
                .toList();
    }

    public ClassificationLowerResponseDto getMiddleClassifications(Long bigId) {
        Classification classification = classificationRepository.findById(bigId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 대분류를 찾을 수 없습니다. bigId=" + bigId
                ));

        List<ClassificationResponseDto> middleClassifications = middleClassificationRepository
                .findAllByClassificationId(bigId).stream()
                .map(middleClassification -> new ClassificationResponseDto(
                        middleClassification.getId(),
                        middleClassification.getMiddleName()
                ))
                .toList();

        return new ClassificationLowerResponseDto(
                classification.getId(),
                classification.getBigName(),
                middleClassifications
        );
    }

    public ClassificationLowerResponseDto getDetailClassifications(Long middleId) {
        MiddleClassification middleClassification = middleClassificationRepository.findById(middleId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 중분류를 찾을 수 없습니다. middleId=" + middleId
                ));

        List<ClassificationResponseDto> detailClassifications = detailClassificationRepository
                .findAllByMiddleClassificationId(middleId).stream()
                .map(detailClassification -> new ClassificationResponseDto(
                        detailClassification.getId(),
                        detailClassification.getDetailName()
                ))
                .toList();

        return new ClassificationLowerResponseDto(
                middleClassification.getId(),
                middleClassification.getMiddleName(),
                detailClassifications
        );
    }
}

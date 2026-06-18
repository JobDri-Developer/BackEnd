package com.jobdri.jobdri_api.domain.classification.dto;

public record ClassificationHierarchy(
        Long detailClassificationId,
        String detailClassificationName,
        Long middleClassificationId,
        String middleClassificationName,
        String bigClassificationName
) {
}

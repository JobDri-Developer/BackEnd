package com.jobdri.jobdri_api.domain.classification.mapper;

import com.jobdri.jobdri_api.domain.classification.dto.ClassificationHierarchy;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;

public final class ClassificationHierarchyMapper {

    private ClassificationHierarchyMapper() {
    }

    public static ClassificationHierarchy toHierarchy(DetailClassification detailClassification) {
        return new ClassificationHierarchy(
                detailClassification.getId(),
                detailClassification.getDetailName(),
                detailClassification.getMiddleClassification().getId(),
                detailClassification.getMiddleClassification().getMiddleName(),
                detailClassification.getMiddleClassification().getClassification().getBigName()
        );
    }
}

package com.jobdri.jobdri_api.domain.classification.dto.response;

import java.util.List;

public record ClassificationLowerResponseDto(Long upperClassId,
                                             String upperClassName,
                                             List<ClassificationResponseDto> lowerClassifications) {
}

package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import java.util.List;

public record JobPostingIngestValidationErrorResponse(
        String reason,
        String message,
        List<InvalidField> invalidFields
) {

    public record InvalidField(
            String field,
            String label,
            String message
    ) {
    }
}

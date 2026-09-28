package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import java.util.List;

public record JobApplicationNotReadyResponse(List<String> missingFields) {
}

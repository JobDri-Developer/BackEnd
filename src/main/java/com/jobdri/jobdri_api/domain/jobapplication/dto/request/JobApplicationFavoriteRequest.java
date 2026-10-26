package com.jobdri.jobdri_api.domain.jobapplication.dto.request;

import jakarta.validation.constraints.NotNull;

public record JobApplicationFavoriteRequest(@NotNull Boolean favorite) {}

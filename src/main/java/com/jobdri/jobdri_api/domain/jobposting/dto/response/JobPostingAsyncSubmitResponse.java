package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobPostingAsyncSubmitResponse {

    private String taskId;
    private String status;
    private String message;
}

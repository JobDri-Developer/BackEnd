package com.jobdri.jobdri_api.domain.jobapplication.dto.response;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationSort;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import java.util.List;

public record JobApplicationBoardResponse(JobApplicationSort sort, List<Column> columns) {
    public record Column(JobApplicationStage stage, int count, List<JobApplicationCardResponse> cards) {}
}

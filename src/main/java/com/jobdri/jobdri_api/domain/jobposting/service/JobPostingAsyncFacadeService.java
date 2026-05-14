package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestCommand;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingIngestMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingAsyncSubmitResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class JobPostingAsyncFacadeService {

    private final JobPostingAsyncTaskService jobPostingAsyncTaskService;
    private final JobPostingAsyncProcessor jobPostingAsyncProcessor;

    public JobPostingAsyncSubmitResponse submit(JobPostingIngestMultipartRequest request) {
        String taskId = jobPostingAsyncTaskService.createPendingTask();
        JobPostingIngestCommand command = snapshot(request);

        try {
            jobPostingAsyncProcessor.process(taskId, command);
            return new JobPostingAsyncSubmitResponse(
                    taskId,
                    "PENDING",
                    "채용 공고 비동기 작업이 접수되었습니다."
            );
        } catch (TaskRejectedException e) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "현재 비동기 작업이 많아 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    public JobPostingAsyncStatusResponse getTask(String taskId) {
        return jobPostingAsyncTaskService.getTask(taskId);
    }

    private JobPostingIngestCommand snapshot(JobPostingIngestMultipartRequest request) {
        return JobPostingIngestCommand.builder()
                .rawText(request.rawText())
                .sourceUrl(request.sourceUrl())
                .imageBytes(readBytes(request.image()))
                .imageContentType(readContentType(request.image()))
                .companySize(request.companySize())
                .candidateLimit(request.candidateLimit())
                .build();
    }

    private byte[] readBytes(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }

        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "이미지 파일을 읽을 수 없습니다.");
        }
    }

    private String readContentType(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        return image.getContentType();
    }
}

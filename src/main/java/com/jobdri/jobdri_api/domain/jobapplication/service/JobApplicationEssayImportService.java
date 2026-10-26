package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssaySourceResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationEssayPreviewResponse;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationEssayRepository;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.pagination.PaginationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationEssayImportService {
    private final UserService userService;
    private final JobApplicationRepository applications;
    private final JobApplicationEssayRepository essays;

    public Page<JobApplicationEssaySourceResponse> sources(User principal, Long targetId, String query,
                                                           boolean includeArchived, int page, int size) {
        User user = userService.validateUser(principal);
        validateOwner(user, targetId);
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return essays.findSources(user.getId(), targetId, pattern, includeArchived,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), PaginationPolicy.MAX_PAGE_SIZE)));
    }

    public List<JobApplicationEssayPreviewResponse> previews(User principal, Long targetId, Long sourceId) {
        User user = userService.validateUser(principal);
        validateOwner(user, targetId);
        if (Objects.equals(targetId, sourceId)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "현재 공고는 가져오기 대상으로 선택할 수 없습니다.");
        }
        validateOwner(user, sourceId);
        return essays.findPreviews(user.getId(), sourceId);
    }

    private void validateOwner(User user, Long id) {
        var application = applications.findById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_APPLICATION_NOT_FOUND));
        if (!application.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
    }
}

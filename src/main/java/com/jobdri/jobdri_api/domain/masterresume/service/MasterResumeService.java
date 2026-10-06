package com.jobdri.jobdri_api.domain.masterresume.service;

import com.jobdri.jobdri_api.domain.masterresume.dto.MasterResumeRequest;
import com.jobdri.jobdri_api.domain.masterresume.dto.MasterResumeResponse;
import com.jobdri.jobdri_api.domain.masterresume.entity.MasterResume;
import com.jobdri.jobdri_api.domain.masterresume.repository.MasterResumeRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MasterResumeService {
    private final MasterResumeRepository resumes;
    private final UserService users;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MasterResumeResponse get(User principal) {
        User user = users.validateUser(principal);
        return resumes.findByUserId(user.getId()).map(MasterResumeResponse::from)
                .orElseGet(MasterResumeResponse::empty);
    }

    @Transactional
    public MasterResumeResponse save(User principal, MasterResumeRequest request) {
        User user = users.validateUser(principal);
        userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if ((request.gpa() == null) != (request.maxGpa() == null)
                || request.gpa() != null && request.gpa().compareTo(request.maxGpa()) > 0) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,
                    "학점과 만점을 모두 입력하고 학점은 만점 이하로 설정해주세요.");
        }
        MasterResume resume = resumes.findByUserId(user.getId()).orElseGet(() -> MasterResume.create(user));
        if (resume.getId() != null && (request.expectedRevision() == null
                || request.expectedRevision() != resume.getContentRevision())) {
            throw new GeneralException(GeneralErrorCode.MASTER_RESUME_UPDATE_CONFLICT,
                    "마스터 이력서가 이미 수정되었습니다. 최신 내용을 다시 조회해주세요.");
        }
        resume.replace(request.gpa(), request.maxGpa(), normalizeMetrics(request.metrics()),
                normalizeExperiences(request.experiences()));
        return MasterResumeResponse.from(resumes.saveAndFlush(resume));
    }

    private List<MasterResume.Metric> normalizeMetrics(List<MasterResumeRequest.Metric> rows) {
        return (rows == null ? List.<MasterResumeRequest.Metric>of() : rows).stream()
                .filter(Objects::nonNull)
                .filter(row -> hasText(row.name()))
                .map(row -> {
                    if (row.type() == null) {
                        throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "정량 스펙 유형은 필수입니다.");
                    }
                    String value = hasText(row.value()) ? row.value().trim() : switch (row.type()) {
                        case CERTIFICATE -> "보유";
                        case AWARD -> "취득일 미입력";
                        default -> null;
                    };
                    if (value == null) {
                        throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,
                                "어학 성적과 기타 정량 정보에는 값을 입력해주세요.");
                    }
                    return new MasterResume.Metric(row.type(), row.name().trim(), value);
                }).toList();
    }

    private List<MasterResume.ExperienceItem> normalizeExperiences(List<MasterResumeRequest.Experience> rows) {
        return (rows == null ? List.<MasterResumeRequest.Experience>of() : rows).stream()
                .filter(Objects::nonNull).filter(row -> hasText(row.name()))
                .map(row -> new MasterResume.ExperienceItem(row.name().trim(),
                        row.period() == null ? null : row.period().trim(),
                        row.description() == null ? null : row.description().trim()))
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.jobposting.service.MockJobPostingGenerationService;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeItemResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplySequenceResponse;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockApplyService {
    private final MockApplyRepository mockApplyRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final MockJobPostingGenerationService mockJobPostingGenerationService;
    private final JobPostingService jobPostingService;
    private final UserService userService;

    @Transactional
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createActualApply(User user, Long jobPostingId) {
        return createActualApply(user, jobPostingId, null);
    }

    @Transactional
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createActualApply(User user, Long jobPostingId, Integer sequence) {
        User validatedUser = userService.validateUser(user);
        JobPosting jobPosting = jobPostingService.getOwnedJobPosting(validatedUser, jobPostingId);

        MockApply mockApply = MockApply.create(
                validatedUser,
                jobPosting,
                ApplyType.ACTUAL,
                resolveSequence(validatedUser, jobPosting, sequence)
        );
        return MockApplyCreateResponse.from(mockApplyRepository.save(mockApply));
    }

    @Transactional
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createMockApplyFromJobPosting(User user, Long jobPostingId) {
        return createMockApplyFromJobPosting(user, jobPostingId, null);
    }

    @Transactional
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createMockApplyFromJobPosting(User user, Long jobPostingId, Integer sequence) {
        User validatedUser = userService.validateUser(user);
        JobPosting jobPosting = jobPostingService.getOwnedJobPosting(validatedUser, jobPostingId);

        MockApply mockApply = MockApply.create(
                validatedUser,
                jobPosting,
                ApplyType.MOCK,
                resolveSequence(validatedUser, jobPosting, sequence)
        );
        return MockApplyCreateResponse.from(mockApplyRepository.save(mockApply));
    }

    @Transactional
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createMockApply(User user, MockApplyCreateMockRequest request) {
        User validatedUser = userService.validateUser(user);
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.COMPANY_NOT_FOUND,
                        "해당 회사를 찾을 수 없습니다. companyId=" + request.companyId()
                ));

        JobPostingMockGenerateResponse generated =
                mockJobPostingGenerationService.generate(request.toJobPostingMockGenerateRequest());

        JobPostingCreateRequest createRequest = new JobPostingCreateRequest(
                company.getName(),
                company.getSize(),
                request.detailClassificationId(),
                generated.task(),
                generated.requirement(),
                generated.preferred()
        );
        Long savedJobPostingId = jobPostingService.createJobPosting(validatedUser, createRequest).getJobPostingId();
        JobPosting savedJobPosting = jobPostingRepository.findById(savedJobPostingId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.JOB_POSTING_NOT_FOUND,
                        "생성된 모의 공고를 찾을 수 없습니다. jobPostingId=" + savedJobPostingId
                ));

        MockApply mockApply = MockApply.create(
                validatedUser,
                savedJobPosting,
                ApplyType.MOCK,
                resolveSequence(validatedUser, savedJobPosting, request.sequence())
        );
        return MockApplyCreateResponse.from(mockApplyRepository.save(mockApply));
    }

    public JobPostingResponse getMockApplyJobPosting(User user, Long mockApplyId) {
        User validatedUser = userService.validateUser(user);
        MockApply mockApply = getOwnedMockApply(validatedUser, mockApplyId);
        return JobPostingResponse.from(mockApply.getJobPosting());
    }

    public MockApplySequenceResponse getMockApplySequence(User user, Long mockApplyId) {
        User validatedUser = userService.validateUser(user);
        MockApply mockApply = getOwnedMockApply(validatedUser, mockApplyId);
        Long jobPostingId = mockApply.getJobPosting().getId();

        int totalCount = Math.toIntExact(
                mockApplyRepository.countByUserIdAndJobPostingId(validatedUser.getId(), jobPostingId)
        );
        int sequence = mockApplyRepository.calculateSequence(mockApply);
        totalCount = Math.max(totalCount, sequence);

        if (sequence < 1) {
            throw new GeneralException(
                    GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                    "해당 공고에 연결된 모의 서류 지원 순서를 찾을 수 없습니다. mockApplyId=" + mockApplyId
            );
        }

        return new MockApplySequenceResponse(
                jobPostingId,
                mockApply.getId(),
                totalCount,
                sequence
        );
    }

    public MockApplyHomeResponse getMyMockApplies(User user) {
        User validatedUser = userService.validateUser(user);
        List<MockApplyHomeItemResponse> items = mockApplyRepository.findHomeItemsByUserId(validatedUser.getId()).stream()
                .map(MockApplyHomeItemResponse::from)
                .toList();

        return new MockApplyHomeResponse(
                filterByCompletion(items, false),
                filterByCompletion(items, true)
        );
    }

    private List<MockApplyHomeItemResponse> filterByCompletion(
            List<MockApplyHomeItemResponse> items,
            boolean completed
    ) {
        return items.stream()
                .filter(item -> completed == (item.status() == MockApplyStatus.COMPLETED))
                .collect(Collectors.toList());
    }

    private MockApply getOwnedMockApply(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findById(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }

        return mockApply;
    }

    private int resolveSequence(User user, JobPosting jobPosting, Integer requestedSequence) {
        if (requestedSequence != null) {
            return requestedSequence;
        }
        return Math.toIntExact(mockApplyRepository.countByUserIdAndJobPostingId(
                user.getId(),
                jobPosting.getId()
        )) + 1;
    }
}

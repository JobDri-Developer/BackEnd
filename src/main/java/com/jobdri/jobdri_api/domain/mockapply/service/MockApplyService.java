package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.jobposting.service.MockJobPostingGenerationService;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeItemResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyRetryResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplySequenceResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyUpdateNameResponse;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.pagination.PaginationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockApplyService {
    public static final int MAX_PAGE_SIZE = PaginationPolicy.MAX_PAGE_SIZE;
    private static final int SEQUENCE_SAVE_MAX_RETRY = 5;
    private static final int SEQUENCE_ALLOCATE_MAX_RETRY = 5;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final String SEQUENCE_UNIQUE_CONSTRAINT = "uk_mock_apply_user_posting_sequence";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final MockApplyRepository mockApplyRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final AnalysisRepository analysisRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final QuestionRepository questionRepository;
    private final MockJobPostingGenerationService mockJobPostingGenerationService;
    private final JobPostingService jobPostingService;
    private final UserService userService;
    private final MockApplyPersistenceService mockApplyPersistenceService;
    private final MockApplySequenceService mockApplySequenceService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createActualApply(User user, Long jobPostingId) {
        return createActualApply(user, jobPostingId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createActualApply(User user, Long jobPostingId, Integer sequence) {
        User validatedUser = userService.validateUser(user);
        JobPosting jobPosting = jobPostingService.getOwnedJobPosting(validatedUser, jobPostingId);

        MockApply mockApply = saveMockApplyWithSequence(
                validatedUser,
                jobPosting,
                ApplyType.ACTUAL,
                sequence
        );
        return MockApplyCreateResponse.from(mockApply);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLogEvent(action = "MOCK_APPLY_RETRY", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyRetryResponse retryMockApply(User user, Long mockApplyId) {
        User validatedUser = userService.validateUser(user);
        MockApply sourceMockApply = getOwnedMockApplyWithJobPosting(validatedUser, mockApplyId);
        JobPosting sourceJobPosting = sourceMockApply.getJobPosting();

        MockApply retryMockApply = saveMockApplyWithSequence(
                validatedUser,
                sourceJobPosting,
                sourceMockApply.getApplyType(),
                null
        );

        List<Question> sourceQuestions = questionRepository.findAllByMockApplyIdOrderByIdAsc(sourceMockApply.getId());
        if (!sourceQuestions.isEmpty()) {
            MockApply targetMockApply = retryMockApply;
            List<Question> retryQuestions = sourceQuestions.stream()
                    .map(question -> Question.create(
                            targetMockApply,
                            question.getContent(),
                            question.getLimit(),
                            ""
                    ))
                    .toList();
            mockApplyPersistenceService.saveQuestions(retryQuestions);
            retryMockApply.updateStatus(MockApplyStatus.ANSWER_WRITE);
            retryMockApply = mockApplyPersistenceService.saveAndFlush(retryMockApply);
        }

        return MockApplyRetryResponse.of(sourceMockApply.getId(), retryMockApply);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createMockApplyFromJobPosting(User user, Long jobPostingId) {
        return createMockApplyFromJobPosting(user, jobPostingId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLogEvent(action = "MOCK_APPLY_CREATE", targetType = "MOCK_APPLY", targetId = "#result.mockApplyId()")
    public MockApplyCreateResponse createMockApplyFromJobPosting(User user, Long jobPostingId, Integer sequence) {
        User validatedUser = userService.validateUser(user);
        JobPosting jobPosting = jobPostingService.getOwnedJobPosting(validatedUser, jobPostingId);

        MockApply mockApply = saveMockApplyWithSequence(
                validatedUser,
                jobPosting,
                ApplyType.MOCK,
                sequence
        );
        return MockApplyCreateResponse.from(mockApply);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
                JobPostingProfileColor.DEFAULT,
                generated.jobTitle(),
                company.getName(),
                company.getSize(),
                generated.jobTitle(),
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

        MockApply mockApply = saveMockApplyWithSequence(
                validatedUser,
                savedJobPosting,
                ApplyType.MOCK,
                request.sequence()
        );
        return MockApplyCreateResponse.from(mockApply);
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

    public MockApplyHomeResponse getMyMockApplies(User user, int page, int size) {
        User validatedUser = userService.validateUser(user);
        List<MockApplyHomeItemResponse> inProgressItems = mockApplyRepository
                .findAllByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(validatedUser.getId(), MockApplyStatus.COMPLETED)
                .stream()
                .map(MockApplyHomeItemResponse::from)
                .toList();
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        Page<MockApplyHomeItemResponse> completedItems = mockApplyRepository
                .findAllByUserIdAndStatus(validatedUser.getId(), MockApplyStatus.COMPLETED, pageable)
                .map(MockApplyHomeItemResponse::from);

        return new MockApplyHomeResponse(
                inProgressItems,
                completedItems
        );
    }

    @Transactional
    @AuditLogEvent(action = "MOCK_APPLY_NAME_UPDATE", targetType = "MOCK_APPLY", targetId = "#arg1")
    public MockApplyUpdateNameResponse updateMockApplyName(User user, Long mockApplyId, String name) {
        User validatedUser = userService.validateUser(user);
        MockApply mockApply = getOwnedMockApply(validatedUser, mockApplyId);
        String trimmedName = validateAndTrimDisplayName(name);
        mockApply.updateDisplayName(trimmedName);
        MockApply savedMockApply = mockApplyRepository.saveAndFlush(mockApply);
        return MockApplyUpdateNameResponse.from(savedMockApply);
    }

    @Transactional
    @AuditLogEvent(action = "MOCK_APPLY_DELETE", targetType = "MOCK_APPLY", targetId = "#arg1")
    public void deleteMockApply(User user, Long mockApplyId) {
        User validatedUser = userService.validateUser(user);
        getOwnedMockApply(validatedUser, mockApplyId);

        questionAnalysisRepository.deleteAllByMockApplyId(mockApplyId);
        analysisRepository.deleteByMockApplyId(mockApplyId);
        questionRepository.deleteAllByMockApplyId(mockApplyId);
        mockApplyRepository.deleteByMockApplyId(mockApplyId);
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

    private MockApply getOwnedMockApplyWithJobPosting(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findByIdWithJobPosting(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }

        return mockApply;
    }

    private String validateAndTrimDisplayName(String name) {
        if (name == null || name.isBlank()) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이름은 필수입니다."
            );
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이름은 최대 100자까지 입력할 수 있습니다."
            );
        }
        return trimmedName;
    }

    private int resolveSequence(User user, JobPosting jobPosting, Integer requestedSequence) {
        if (isPositiveSequence(requestedSequence)) {
            return requestedSequence;
        }
        return allocateSequence(user, jobPosting);
    }

    private int allocateSequence(User user, JobPosting jobPosting) {
        for (int attempt = 0; attempt < SEQUENCE_ALLOCATE_MAX_RETRY; attempt++) {
            try {
                return mockApplySequenceService.allocate(user.getId(), jobPosting.getId());
            } catch (DataIntegrityViolationException e) {
                if (attempt == SEQUENCE_ALLOCATE_MAX_RETRY - 1) {
                    throw new GeneralException(
                            GeneralErrorCode.INTERNAL_SERVER_ERROR,
                            "모의 서류 지원 순번 할당에 실패했습니다."
                    );
                }
            }
        }

        throw new GeneralException(
                GeneralErrorCode.INTERNAL_SERVER_ERROR,
                "모의 서류 지원 순번 할당에 실패했습니다."
        );
    }

    private boolean isPositiveSequence(Integer sequence) {
        return sequence != null && sequence > 0;
    }

    private MockApply saveMockApplyWithSequence(
            User user,
            JobPosting jobPosting,
            ApplyType applyType,
            Integer requestedSequence
    ) {
        int sequence = resolveSequence(user, jobPosting, requestedSequence);
        for (int attempt = 0; attempt < SEQUENCE_SAVE_MAX_RETRY; attempt++) {
            try {
                return mockApplyPersistenceService.saveAndFlush(MockApply.create(user, jobPosting, applyType, sequence));
            } catch (DataIntegrityViolationException e) {
                if (!isSequenceUniqueConflict(e)) {
                    throw e;
                }
                if (isPositiveSequence(requestedSequence)) {
                    throw new GeneralException(
                            GeneralErrorCode.INVALID_PARAMETER,
                            "이미 사용 중인 지원 순번입니다. sequence=" + requestedSequence
                    );
                }
                sequence++;
            }
        }

        throw new GeneralException(
            GeneralErrorCode.INTERNAL_SERVER_ERROR,
            "모의 서류 지원 순번 생성에 실패했습니다."
        );
    }

    private boolean isSequenceUniqueConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
                    && isSequenceConstraintName(constraintViolation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())
                    && containsSequenceConstraint(sqlException.getMessage())) {
                return true;
            }
            if (containsSequenceConstraint(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isSequenceConstraintName(String constraintName) {
        return containsSequenceConstraint(constraintName);
    }

    private boolean containsSequenceConstraint(String value) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(SEQUENCE_UNIQUE_CONSTRAINT);
    }
}

package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationPositionRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationSort;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationBoardResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationCardResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationEssayRepository;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationBoardService {
    private final JobApplicationRepository applications;
    private final JobApplicationEssayRepository essays;
    private final UserService userService;
    private final UserRepository users;

    public JobApplicationBoardResponse getBoard(User principal, String query, JobApplicationSort sort) {
        User user = userService.validateUser(principal);
        JobApplicationSort effectiveSort = sort == null ? JobApplicationSort.MANUAL : sort;
        List<JobApplication> cards = applications.findBoard(user.getId(), searchPattern(query));
        Map<Long, Long> counts = cards.isEmpty() ? Map.of() : essays.countActiveByUser(user.getId()).stream()
                .collect(Collectors.toMap(JobApplicationEssayRepository.Count::getJobApplicationId,
                        JobApplicationEssayRepository.Count::getQuestionCount));
        Comparator<JobApplication> comparator = effectiveSort == JobApplicationSort.CREATED_DESC
                ? Comparator.comparing(JobApplication::getCreatedAt).reversed()
                    .thenComparing(JobApplication::getId, Comparator.reverseOrder())
                : Comparator.comparingInt(JobApplication::getStageOrder).thenComparing(JobApplication::getId);
        List<JobApplicationBoardResponse.Column> columns = Arrays.stream(JobApplicationStage.values())
                .map(stage -> {
                    List<JobApplicationCardResponse> items = cards.stream()
                            .filter(card -> card.getStage() == stage).sorted(comparator)
                            .map(card -> JobApplicationCardResponse.from(card, counts.getOrDefault(card.getId(), 0L)))
                            .toList();
                    return new JobApplicationBoardResponse.Column(stage, items.size(), items);
                }).toList();
        return new JobApplicationBoardResponse(effectiveSort, columns);
    }

    @Transactional
    @AuditLogEvent(action = "JOB_APPLICATION_MOVE", targetType = "JOB_APPLICATION", targetId = "#arg1")
    public JobApplicationBoardResponse move(User principal, Long id, JobApplicationPositionRequest request) {
        if (request == null || request.targetStage() == null || request.targetIndex() == null
                || request.targetIndex() < 0) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "단계와 0 이상의 targetIndex가 필요합니다.");
        }
        User user = userService.validateUser(principal);
        // Registration uses the same user lock. It protects empty columns and prevents
        // concurrent inserts while either column is being reordered.
        users.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        JobApplication card = applications.findById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_APPLICATION_NOT_FOUND));
        if (!card.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 카드에 접근할 수 없습니다.");
        }
        if (card.getArchivedAt() != null) {
            throw new GeneralException(GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT, "보관된 지원 카드는 이동할 수 없습니다.");
        }
        JobApplicationStage source = card.getStage();
        JobApplicationStage target = request.targetStage();
        List<JobApplication> locked = applications.lockActiveColumns(user.getId(),
                source == target ? List.of(source) : List.of(source, target));
        List<JobApplication> destination = new ArrayList<>(locked.stream()
                .filter(item -> item.getStage() == target && !item.getId().equals(id)).toList());
        if (request.targetIndex() > destination.size()) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "targetIndex가 도착 열의 범위를 벗어났습니다.");
        }
        if (source != target) {
            reorder(locked.stream().filter(item -> item.getStage() == source && !item.getId().equals(id)).toList(), source);
        }
        destination.add(request.targetIndex(), card);
        reorder(destination, target);
        // Return timestamps after JPA auditing has run.
        applications.flush();
        return getBoard(user, null, JobApplicationSort.MANUAL);
    }

    private static void reorder(List<JobApplication> cards, JobApplicationStage stage) {
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).moveTo(stage, i);
        }
    }

    private static String searchPattern(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}

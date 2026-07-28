package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionAnswerSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionCandidateCreateRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionSelectionSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnswerResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionSelectionResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuestionServiceTest {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionAnalysisRepository questionAnalysisRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private MockApplyRepository mockApplyRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ClassificationRepository classificationRepository;

    @Autowired
    private DetailClassificationRepository detailClassificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("선택 문항을 저장하고 지원 상태를 답변 작성 단계로 변경한다")
    void saveSelectedQuestions() {
        User user = saveUser("question-save@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionSaveRequest request = new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("지원 동기와 입사 후 목표를 작성해주세요.", 700, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("직접 추가한 문항입니다.", null, true)
        ));

        QuestionSelectionResponse response = questionService.saveSelectedQuestions(user, mockApply.getId(), request);

        assertThat(response.mockApplyId()).isEqualTo(mockApply.getId());
        assertThat(response.status()).isEqualTo(MockApplyStatus.ANSWER_WRITE);
        assertThat(response.questions()).hasSize(2);
        assertThat(response.questions().get(0).content()).isEqualTo("지원 동기와 입사 후 목표를 작성해주세요.");
        assertThat(response.questions().get(0).charLimit()).isEqualTo(700);
        assertThat(response.questions().get(0).custom()).isFalse();
        assertThat(response.questions().get(1).content()).isEqualTo("직접 추가한 문항입니다.");
        assertThat(response.questions().get(1).charLimit()).isEqualTo(1000);
        assertThat(response.questions().get(1).custom()).isTrue();
        assertThat(mockApply.getStatus()).isEqualTo(MockApplyStatus.ANSWER_WRITE);
        assertThat(questionRepository.findAllByMockApplyId(mockApply.getId())).hasSize(2);
    }

    @Test
    @DisplayName("문항 저장 시 기존 선택 문항을 새 선택 목록으로 교체한다")
    void saveSelectedQuestionsReplacesExistingQuestions() {
        User user = saveUser("question-replace@example.com");
        MockApply mockApply = saveMockApply(user);
        questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("기존 문항 1", 500, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("기존 문항 2", 500, false)
        )));

        QuestionSelectionResponse response = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("새 문항", 800, false)
        )));

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).content()).isEqualTo("새 문항");
        assertThat(response.questions().get(0).custom()).isTrue();
        assertThat(questionRepository.findAllByMockApplyId(mockApply.getId()))
                .extracting(Question::getContent)
                .containsExactly("새 문항");
    }

    @Test
    @DisplayName("선택 문항 조회 시 직접 추가 여부를 함께 반환한다")
    void getSelectedQuestionsReturnsCustomFlag() {
        User user = saveUser("question-selected-custom-flag@example.com");
        MockApply mockApply = saveMockApply(user);
        questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("지원 동기와 입사 후 목표를 작성해주세요.", 700, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("직접 추가한 문항입니다.", 1000, true)
        )));

        QuestionSelectionResponse response = questionService.getSelectedQuestions(user, mockApply.getId());

        assertThat(response.questions()).hasSize(2);
        assertThat(response.questions())
                .extracting("content", "custom")
                .containsExactlyInAnyOrder(
                        tuple("지원 동기와 입사 후 목표를 작성해주세요.", false),
                        tuple("직접 추가한 문항입니다.", true)
                );
    }

    @Test
    @DisplayName("직접 추가 문항은 선택 문항으로 저장하지 않고 후보 목록에 추가한다")
    void addCustomQuestionCandidate() {
        User user = saveUser("question-custom-candidate@example.com");
        MockApply mockApply = saveMockApply(user);

        QuestionCandidateResponse response = questionService.addCustomQuestionCandidate(
                user,
                mockApply.getId(),
                new QuestionCandidateCreateRequest("직접 추가한 후보 문항입니다.", 500)
        );
        List<QuestionCandidateResponse> candidates = questionService.getQuestionCandidates(user, mockApply.getId());

        assertThat(response.content()).isEqualTo("직접 추가한 후보 문항입니다.");
        assertThat(response.charLimit()).isEqualTo(500);
        assertThat(response.selected()).isFalse();
        assertThat(response.custom()).isTrue();
        assertThat(questionRepository.findAllByMockApplyId(mockApply.getId())).isEmpty();
        assertThat(candidates).hasSize(6);
        assertThat(candidates.get(5).questionId()).isEqualTo(response.questionId());
        assertThat(candidates.get(5).content()).isEqualTo("직접 추가한 후보 문항입니다.");
        assertThat(candidates.get(5).selected()).isFalse();
        assertThat(candidates.get(5).custom()).isTrue();
    }

    @Test
    @DisplayName("기본 후보와 같은 내용은 직접 추가 문항 후보로 등록할 수 없다")
    void addCustomQuestionCandidateThrowsWhenContentExistsInDefaultCandidate() {
        User user = saveUser("question-custom-default-duplicate@example.com");
        MockApply mockApply = saveMockApply(user);

        assertThatThrownBy(() -> questionService.addCustomQuestionCandidate(
                user,
                mockApply.getId(),
                new QuestionCandidateCreateRequest("지원 동기와 입사 후 목표를 작성해주세요.", 700)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
        assertThat(questionRepository.findAllByMockApplyId(mockApply.getId())).isEmpty();
        assertThat(questionService.getQuestionCandidates(user, mockApply.getId())).hasSize(5);
    }

    @Test
    @DisplayName("같은 직접 추가 문항 후보를 여러 번 요청하면 기존 후보를 반환한다")
    void addCustomQuestionCandidateReturnsExistingCandidateWhenDuplicated() {
        User user = saveUser("question-custom-duplicate@example.com");
        MockApply mockApply = saveMockApply(user);

        QuestionCandidateResponse first = questionService.addCustomQuestionCandidate(
                user,
                mockApply.getId(),
                new QuestionCandidateCreateRequest("중복 직접 추가 문항입니다.", 500)
        );
        QuestionCandidateResponse second = questionService.addCustomQuestionCandidate(
                user,
                mockApply.getId(),
                new QuestionCandidateCreateRequest("중복 직접 추가 문항입니다.", 800)
        );
        List<QuestionCandidateResponse> candidates = questionService.getQuestionCandidates(user, mockApply.getId());

        assertThat(second.questionId()).isEqualTo(first.questionId());
        assertThat(second.content()).isEqualTo(first.content());
        assertThat(second.charLimit()).isEqualTo(first.charLimit());
        assertThat(second.selected()).isFalse();
        assertThat(second.custom()).isTrue();
        assertThat(questionRepository.findAllByMockApplyId(mockApply.getId())).isEmpty();
        assertThat(candidates).hasSize(6);
        assertThat(candidates.stream()
                .filter(QuestionCandidateResponse::custom)
                .map(QuestionCandidateResponse::content)
                .toList())
                .containsExactly("중복 직접 추가 문항입니다.");
    }

    @Test
    @DisplayName("이미 선택된 직접 추가 후보를 다시 요청하면 선택 상태로 반환한다")
    void addCustomQuestionCandidateReturnsSelectedStateWhenAlreadySelected() {
        User user = saveUser("question-custom-selected@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionCandidateResponse first = questionService.addCustomQuestionCandidate(
                user,
                mockApply.getId(),
                new QuestionCandidateCreateRequest("이미 선택된 직접 추가 문항입니다.", 500)
        );
        questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("이미 선택된 직접 추가 문항입니다.", 500, true)
        )));

        QuestionCandidateResponse second = questionService.addCustomQuestionCandidate(
                user,
                mockApply.getId(),
                new QuestionCandidateCreateRequest("이미 선택된 직접 추가 문항입니다.", 800)
        );

        assertThat(second.questionId()).isEqualTo(first.questionId());
        assertThat(second.content()).isEqualTo(first.content());
        assertThat(second.charLimit()).isEqualTo(first.charLimit());
        assertThat(second.selected()).isTrue();
        assertThat(second.custom()).isTrue();
    }

    @Test
    @DisplayName("문항 후보 목록은 이미 저장된 기본 문항을 선택 상태로 반환한다")
    void getQuestionCandidatesMarksSelectedQuestion() {
        User user = saveUser("question-candidates@example.com");
        MockApply mockApply = saveMockApply(user);
        questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("지원 동기와 입사 후 목표를 작성해주세요.", 700, false)
        )));

        List<QuestionCandidateResponse> candidates = questionService.getQuestionCandidates(user, mockApply.getId());

        assertThat(candidates).hasSize(5);
        assertThat(candidates)
                .extracting(QuestionCandidateResponse::questionId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(candidates)
                .extracting(QuestionCandidateResponse::custom)
                .containsOnly(false);
        assertThat(candidates.get(0).selected()).isTrue();
        assertThat(candidates.get(1).selected()).isFalse();
    }

    @Test
    @DisplayName("선택 문항은 1개 이상이어야 한다")
    void saveSelectedQuestionsThrowsWhenEmpty() {
        User user = saveUser("question-empty@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionSaveRequest request = new QuestionSelectionSaveRequest(List.of());

        assertThatThrownBy(() -> questionService.saveSelectedQuestions(user, mockApply.getId(), request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("선택 문항은 최대 5개까지 저장할 수 있다")
    void saveSelectedQuestionsThrowsWhenTooMany() {
        User user = saveUser("question-too-many@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionSaveRequest request = new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("문항 1", 500, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("문항 2", 500, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("문항 3", 500, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("문항 4", 500, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("문항 5", 500, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("문항 6", 500, false)
        ));

        assertThatThrownBy(() -> questionService.saveSelectedQuestions(user, mockApply.getId(), request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("다른 사용자의 지원서에는 문항을 저장할 수 없다")
    void saveSelectedQuestionsThrowsWhenUserDoesNotOwnMockApply() {
        User owner = saveUser("question-owner@example.com");
        User other = saveUser("question-other@example.com");
        MockApply mockApply = saveMockApply(owner);
        QuestionSelectionSaveRequest request = new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("지원 동기", 700, false)
        ));

        assertThatThrownBy(() -> questionService.saveSelectedQuestions(other, mockApply.getId(), request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("저장된 문항의 내용과 답변을 함께 작성하거나 수정한다")
    void saveAnswers() {
        User user = saveUser("answer-save@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("지원 동기를 작성해주세요.", 700, false)
        )));
        Long questionId = selected.questions().get(0).questionId();

        QuestionAnswerResponse response = questionService.saveAnswers(user, mockApply.getId(), new QuestionAnswerSaveRequest(List.of(
                new QuestionAnswerSaveRequest.QuestionAnswerItem(
                        questionId,
                        "지원 동기와 입사 후 목표를 작성해주세요.",
                        1000,
                        "저는 백엔드 개발 경험을 바탕으로 지원했습니다."
                )
        )));

        assertThat(response.questions()).hasSize(1);
        assertThat(response.sequence()).isEqualTo(1);
        assertThat(response.questions().get(0).content()).isEqualTo("지원 동기와 입사 후 목표를 작성해주세요.");
        assertThat(response.questions().get(0).answer()).isEqualTo("저는 백엔드 개발 경험을 바탕으로 지원했습니다.");
        Question savedQuestion = questionRepository.findById(questionId).orElseThrow();
        assertThat(savedQuestion.getContent()).isEqualTo("지원 동기와 입사 후 목표를 작성해주세요.");
        assertThat(savedQuestion.getLimit()).isEqualTo(1000);
        assertThat(savedQuestion.getAnswer()).isEqualTo("저는 백엔드 개발 경험을 바탕으로 지원했습니다.");
    }

    @Test
    @DisplayName("답변 저장은 선택 저장에서 허용한 짧은 문항 내용도 동일하게 허용한다")
    void saveAnswersAllowsShortQuestionContentAcceptedBySelectionSave() {
        User user = saveUser("answer-short-question@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("새 문항", 700, true)
        )));
        Long questionId = selected.questions().get(0).questionId();

        QuestionAnswerResponse response = questionService.saveAnswers(user, mockApply.getId(), new QuestionAnswerSaveRequest(List.of(
                new QuestionAnswerSaveRequest.QuestionAnswerItem(questionId, "새 문항", 700, "새 문항 답변입니다.")
        )));

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).content()).isEqualTo("새 문항");
        assertThat(response.questions().get(0).answer()).isEqualTo("새 문항 답변입니다.");
    }

    @Test
    @DisplayName("답변 저장 요청은 answers 필드명도 questions와 동일하게 역직렬화한다")
    void questionAnswerSaveRequestAcceptsAnswersAlias() throws Exception {
        QuestionAnswerSaveRequest request = objectMapper.readValue(
                """
                        {
                          "answers": [
                            {
                              "questionId": 1,
                              "content": "새 문항",
                              "charLimit": 700,
                              "answer": "새 문항 답변입니다."
                            }
                          ]
                        }
                        """,
                QuestionAnswerSaveRequest.class
        );

        assertThat(request.questions()).hasSize(1);
        assertThat(request.questions().get(0).questionId()).isEqualTo(1L);
        assertThat(request.questions().get(0).content()).isEqualTo("새 문항");
        assertThat(request.questions().get(0).answer()).isEqualTo("새 문항 답변입니다.");
    }

    @Test
    @DisplayName("답변 저장 시 문항 추가 수정 삭제와 글자수 제한을 화면 상태대로 동기화한다")
    void saveAnswersSyncsQuestionList() {
        User user = saveUser("answer-sync@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("기존 문항 1", 700, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("기존 문항 2", 800, false)
        )));
        Long retainedQuestionId = selected.questions().get(0).questionId();
        Long deletedQuestionId = selected.questions().get(1).questionId();

        QuestionAnswerResponse response = questionService.saveAnswers(user, mockApply.getId(), new QuestionAnswerSaveRequest(List.of(
                new QuestionAnswerSaveRequest.QuestionAnswerItem(
                        retainedQuestionId,
                        "수정된 기존 문항",
                        1000,
                        "수정된 기존 문항 답변입니다."
                ),
                new QuestionAnswerSaveRequest.QuestionAnswerItem(
                        null,
                        "새로 추가한 문항",
                        700,
                        "새로 추가한 문항 답변입니다."
                )
        )));

        assertThat(response.questions()).hasSize(2);
        assertThat(response.questions())
                .extracting("content", "charLimit", "answer")
                .containsExactly(
                        tuple("수정된 기존 문항", 1000, "수정된 기존 문항 답변입니다."),
                        tuple("새로 추가한 문항", 700, "새로 추가한 문항 답변입니다.")
                );
        assertThat(questionRepository.findById(deletedQuestionId)).isEmpty();
        assertThat(questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId()))
                .extracting(Question::getContent, Question::getLimit, Question::getAnswer)
                .containsExactly(
                        tuple("수정된 기존 문항", 1000, "수정된 기존 문항 답변입니다."),
                        tuple("새로 추가한 문항", 700, "새로 추가한 문항 답변입니다.")
                );
        assertThat(mockApply.getStatus()).isEqualTo(MockApplyStatus.ANSWER_WRITE);
    }

    @Test
    @DisplayName("답변 저장 응답은 같은 공고 기준 현재 지원 순번을 반환한다")
    void saveAnswersReturnsSequence() {
        User user = saveUser("answer-sequence@example.com");
        JobPosting jobPosting = saveJobPosting();
        mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        MockApply secondMockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, secondMockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("재지원 답변 문항입니다.", 700, false)
        )));
        Long questionId = selected.questions().get(0).questionId();

        QuestionAnswerResponse response = questionService.saveAnswers(user, secondMockApply.getId(), new QuestionAnswerSaveRequest(List.of(
                new QuestionAnswerSaveRequest.QuestionAnswerItem(questionId, "재지원 답변 문항입니다.", 700, "두 번째 지원 답변입니다.")
        )));

        assertThat(response.mockApplyId()).isEqualTo(secondMockApply.getId());
        assertThat(response.sequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("답변 저장 응답은 저장된 지원 순번을 우선 반환한다")
    void saveAnswersReturnsStoredSequence() {
        User user = saveUser("answer-stored-sequence@example.com");
        JobPosting jobPosting = saveJobPosting();
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL, 4));
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("재지원 저장 순번 문항입니다.", 700, false)
        )));
        Long questionId = selected.questions().get(0).questionId();

        QuestionAnswerResponse response = questionService.saveAnswers(user, mockApply.getId(), new QuestionAnswerSaveRequest(List.of(
                new QuestionAnswerSaveRequest.QuestionAnswerItem(questionId, "재지원 저장 순번 문항입니다.", 700, "네 번째 지원 답변입니다.")
        )));

        assertThat(response.mockApplyId()).isEqualTo(mockApply.getId());
        assertThat(response.sequence()).isEqualTo(4);
    }

    @Test
    @DisplayName("해당 지원서에 속하지 않은 문항은 답변 저장에 사용할 수 없다")
    void saveAnswersThrowsWhenQuestionDoesNotBelongToMockApply() {
        User user = saveUser("answer-invalid-question@example.com");
        MockApply mockApply = saveMockApply(user);
        MockApply otherMockApply = saveMockApply(user);
        QuestionSelectionResponse otherSelected = questionService.saveSelectedQuestions(user, otherMockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("다른 지원서 문항", 700, false)
        )));
        Long otherQuestionId = otherSelected.questions().get(0).questionId();

        assertThatThrownBy(() -> questionService.saveAnswers(user, mockApply.getId(), new QuestionAnswerSaveRequest(List.of(
                new QuestionAnswerSaveRequest.QuestionAnswerItem(otherQuestionId, "다른 지원서 문항", 700, "답변")
        ))))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("선택 문항 1개를 삭제하고 남은 문항 목록을 반환한다")
    void deleteQuestion() {
        User user = saveUser("question-delete@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("삭제할 문항", 700, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("남길 문항", 800, false)
        )));
        Long deletedQuestionId = selected.questions().get(0).questionId();

        QuestionSelectionResponse response = questionService.deleteQuestion(user, mockApply.getId(), deletedQuestionId);

        assertThat(response.mockApplyId()).isEqualTo(mockApply.getId());
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).content()).isEqualTo("남길 문항");
        assertThat(questionRepository.findById(deletedQuestionId)).isEmpty();
        assertThat(questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId()))
                .extracting(Question::getContent)
                .containsExactly("남길 문항");
    }

    @Test
    @DisplayName("문항 삭제 시 해당 문항의 분석 상세도 함께 삭제한다")
    void deleteQuestionDeletesQuestionAnalyses() {
        User user = saveUser("question-delete-analysis@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("삭제할 분석 문항", 700, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("남길 분석 문항", 800, false)
        )));
        Question deletedQuestion = questionRepository.findById(selected.questions().get(0).questionId()).orElseThrow();
        Question retainedQuestion = questionRepository.findById(selected.questions().get(1).questionId()).orElseThrow();
        Analysis analysis = analysisRepository.save(Analysis.create(mockApply, 80, 80, 80, 80, "분석 피드백"));
        QuestionAnalysis deletedQuestionAnalysis = questionAnalysisRepository.save(QuestionAnalysis.create(
                deletedQuestion,
                analysis,
                "삭제할 문장",
                "삭제할 이유",
                "삭제할 개선안",
                QuestionAnalysisStatus.MENTIONED,
                0,
                5
        ));
        QuestionAnalysis retainedQuestionAnalysis = questionAnalysisRepository.save(QuestionAnalysis.create(
                retainedQuestion,
                analysis,
                "남길 문장",
                "남길 이유",
                "남길 개선안",
                QuestionAnalysisStatus.PROVEN,
                0,
                4
        ));

        questionService.deleteQuestion(user, mockApply.getId(), deletedQuestion.getId());

        assertThat(questionAnalysisRepository.findById(deletedQuestionAnalysis.getId())).isEmpty();
        assertThat(questionAnalysisRepository.findById(retainedQuestionAnalysis.getId())).isPresent();
    }

    @Test
    @DisplayName("마지막 남은 문항은 삭제할 수 없다")
    void deleteQuestionThrowsWhenOnlyOneQuestionRemains() {
        User user = saveUser("question-delete-last@example.com");
        MockApply mockApply = saveMockApply(user);
        QuestionSelectionResponse selected = questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("마지막 문항", 700, false)
        )));

        assertThatThrownBy(() -> questionService.deleteQuestion(user, mockApply.getId(), selected.questions().get(0).questionId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
        assertThat(questionRepository.findAllByMockApplyId(mockApply.getId())).hasSize(1);
    }

    @Test
    @DisplayName("해당 지원서에 속하지 않은 문항은 삭제할 수 없다")
    void deleteQuestionThrowsWhenQuestionDoesNotBelongToMockApply() {
        User user = saveUser("question-delete-invalid@example.com");
        MockApply mockApply = saveMockApply(user);
        MockApply otherMockApply = saveMockApply(user);
        questionService.saveSelectedQuestions(user, mockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("현재 지원서 문항 1", 700, false),
                new QuestionSelectionSaveRequest.QuestionSelectionItem("현재 지원서 문항 2", 700, false)
        )));
        QuestionSelectionResponse otherSelected = questionService.saveSelectedQuestions(user, otherMockApply.getId(), new QuestionSelectionSaveRequest(List.of(
                new QuestionSelectionSaveRequest.QuestionSelectionItem("다른 지원서 문항", 700, false)
        )));

        assertThatThrownBy(() -> questionService.deleteQuestion(user, mockApply.getId(), otherSelected.questions().get(0).questionId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.QUESTION_NOT_FOUND);
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }

    private MockApply saveMockApply(User user) {
        JobPosting jobPosting = saveJobPosting();
        return mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
    }

    private JobPosting saveJobPosting() {
        User user = saveUser("question-jobposting" + System.nanoTime() + "@example.com");
        Company company = companyRepository.save(Company.create("테스트 기업", CompanySize.MEDIUM));
        DetailClassification detailClassification = saveDetailClassification();
        return jobPostingRepository.save(JobPosting.create(
                user,
                company,
                detailClassification,
                "주요 업무",
                "자격 요건",
                "우대 사항"
        ));
    }

    private DetailClassification saveDetailClassification() {
        Classification classification = Classification.create("테스트 대분류 " + System.nanoTime());
        MiddleClassification middleClassification = classification.addMiddleClassification("테스트 중분류");
        DetailClassification detailClassification = middleClassification.addDetailClassification("테스트 소분류");
        classificationRepository.save(classification);
        return detailClassificationRepository.findById(detailClassification.getId()).orElseThrow();
    }
}

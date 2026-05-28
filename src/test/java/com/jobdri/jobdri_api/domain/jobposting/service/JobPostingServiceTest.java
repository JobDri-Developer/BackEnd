package com.jobdri.jobdri_api.domain.jobposting.service;

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
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplySequence;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplySequenceRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobPostingServiceTest {

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private MockApplyRepository mockApplyRepository;

    @Autowired
    private MockApplySequenceRepository mockApplySequenceRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private QuestionAnalysisRepository questionAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ClassificationRepository classificationRepository;

    @Autowired
    private DetailClassificationRepository detailClassificationRepository;

    @Test
    @DisplayName("채용 공고를 삭제하면 연결된 모의 서류 결과도 함께 삭제한다")
    void deleteJobPostingDeletesMockApplyResults() {
        User user = saveUser("job-posting-delete@example.com");
        JobPosting jobPosting = saveJobPosting(user);
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.MOCK, 1));
        Question question = questionRepository.save(Question.create(mockApply, "지원 동기", 1000, "답변입니다."));
        Analysis analysis = analysisRepository.save(Analysis.create(mockApply, 80, 81, 82, 83, "분석 결과입니다."));
        QuestionAnalysis questionAnalysis = questionAnalysisRepository.save(QuestionAnalysis.create(
                question,
                analysis,
                "답변입니다.",
                "근거가 부족합니다.",
                "구체적인 성과를 포함해 답변했습니다.",
                QuestionAnalysisStatus.MENTIONED,
                0,
                5
        ));
        mockApplySequenceRepository.save(MockApplySequence.create(user.getId(), jobPosting.getId(), 1));

        jobPostingService.deleteJobPosting(user, jobPosting.getId());
        jobPostingRepository.flush();

        assertThat(jobPostingRepository.findById(jobPosting.getId())).isEmpty();
        assertThat(mockApplyRepository.findById(mockApply.getId())).isEmpty();
        assertThat(questionRepository.findById(question.getId())).isEmpty();
        assertThat(analysisRepository.findById(analysis.getId())).isEmpty();
        assertThat(questionAnalysisRepository.findById(questionAnalysis.getId())).isEmpty();
        assertThat(mockApplySequenceRepository.findByKeyForUpdate(user.getId(), jobPosting.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 채용 공고는 삭제할 수 없다")
    void deleteJobPostingThrowsWhenUserDoesNotOwnJobPosting() {
        User owner = saveUser("job-posting-delete-owner@example.com");
        User other = saveUser("job-posting-delete-other@example.com");
        JobPosting jobPosting = saveJobPosting(owner);

        assertThatThrownBy(() -> jobPostingService.deleteJobPosting(other, jobPosting.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);

        assertThat(jobPostingRepository.findById(jobPosting.getId())).isPresent();
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }

    private JobPosting saveJobPosting(User user) {
        Company company = companyRepository.save(Company.create("삭제 테스트 기업 " + UUID.randomUUID(), CompanySize.MEDIUM));
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
        Classification classification = Classification.create("삭제 테스트 대분류 " + UUID.randomUUID());
        MiddleClassification middleClassification = classification.addMiddleClassification("삭제 테스트 중분류");
        DetailClassification detailClassification = middleClassification.addDetailClassification("삭제 테스트 소분류");
        classificationRepository.save(classification);
        return detailClassificationRepository.findById(detailClassification.getId()).orElseThrow();
    }
}

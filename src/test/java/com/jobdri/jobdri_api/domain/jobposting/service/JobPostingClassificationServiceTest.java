package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingClassificationCandidateProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingClassificationServiceTest {

    @Mock
    private DetailClassificationRepository detailClassificationRepository;

    @InjectMocks
    private JobPostingClassificationService jobPostingClassificationService;

    @Test
    @DisplayName("후보 검색 쿼리는 rawText를 제외한 구조화 필드만 사용한다")
    void findCandidatesUsesStructuredFieldsOnly() {
        JobPostingExtractResponse extracted = new JobPostingExtractResponse(
                "해커스 교육그룹",
                "클라우드 엔지니어 (CLOUD Engineer)",
                "클라우드 운영 및 관리",
                "1. 리눅스 환경 사용 경험 필수\n2. 네트워크 지식 필수",
                "1. AWS, NCP, GCP, azure 클라우드 운영 경험",
                "해커스 교육그룹 개발자 RECRUIT 소프트웨어 교육 교육 기획",
                0.9
        );
        when(detailClassificationRepository.findTopCandidatesByTrigram(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(5)))
                .thenReturn(List.<JobPostingClassificationCandidateProjection>of());

        jobPostingClassificationService.findCandidates(extracted, 5);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(detailClassificationRepository).findTopCandidatesByTrigram(queryCaptor.capture(), org.mockito.ArgumentMatchers.eq(5));

        String query = queryCaptor.getValue();
        assertThat(query).contains("클라우드 엔지니어 (CLOUD Engineer)");
        assertThat(query).contains("클라우드 운영 및 관리");
        assertThat(query).contains("리눅스 환경 사용 경험 필수");
        assertThat(query).contains("AWS, NCP, GCP, azure 클라우드 운영 경험");
        assertThat(query).doesNotContain("해커스 교육그룹");
        assertThat(query).doesNotContain("소프트웨어 교육");
        assertThat(query).doesNotContain("교육 기획");
    }
}

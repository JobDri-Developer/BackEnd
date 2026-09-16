package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FewShotPrivacyMaskerTest {
    private final FewShotPrivacyMasker masker = new FewShotPrivacyMasker();

    @Test
    @DisplayName("검색 입력의 대표 개인정보 유형을 정해진 토큰으로 마스킹한다")
    void masksRepresentativePersonalInformation() {
        String masked = masker.mask("""
                이름: 홍길동
                주소: 서울시 강남구 테헤란로 1
                이메일 test.user@example.com, 전화 010-1234-5678
                해외 표기 +82 10 9876 5432, 인터넷 전화 070-1111-2222
                주민번호 900101-1234567
                계정: hong_dev
                사번: EMP-1024
                포트폴리오 https://example.com/users/hong?q=1
                """);

        assertThat(masked)
                .contains("이름: [NAME]", "주소: [ADDRESS]", "[EMAIL]", "[PHONE]", "[RESIDENT_ID]",
                        "계정: [ACCOUNT]", "사번: [INTERNAL_ID]", "[URL]")
                .doesNotContain("홍길동", "테헤란로", "test.user@example.com", "010-1234-5678",
                        "+82 10 9876 5432", "070-1111-2222", "900101-1234567", "hong_dev",
                        "EMP-1024", "example.com");
    }

    @Test
    @DisplayName("직무 의미가 있는 회사명과 학교명은 라벨 없는 일반 문장에서 유지한다")
    void keepsOrganizationsThatCarryExperienceMeaning() {
        assertThat(masker.mask("네이버에서 검색 API를 개발했고 한국대학교에서 컴퓨터공학을 전공했습니다."))
                .isEqualTo("네이버에서 검색 API를 개발했고 한국대학교에서 컴퓨터공학을 전공했습니다.");
    }
}

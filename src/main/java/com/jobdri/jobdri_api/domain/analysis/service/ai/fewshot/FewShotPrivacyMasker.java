package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class FewShotPrivacyMasker {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}._%+-])[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[a-z]{2,}(?![\\p{L}\\p{N}._%+-])"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>]+"
    );
    private static final Pattern KOREAN_PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:(?:\\+82[- .]?(?:10|2|[3-6][1-5]|70))|0(?:2|1[016789]|[3-6][1-5]|70))[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"
    );
    private static final Pattern RESIDENT_REGISTRATION_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)"
    );
    private static final Pattern LABELED_NAME_PATTERN = Pattern.compile(
            "(?im)(^|\\R)([ \\t]*(?:이름|성명|name)[ \\t]*[:：=][ \\t]*)[^\\r\\n]+"
    );
    private static final Pattern LABELED_ADDRESS_PATTERN = Pattern.compile(
            "(?im)(^|\\R)([ \\t]*(?:주소|address)[ \\t]*[:：=][ \\t]*)[^\\r\\n]+"
    );
    private static final Pattern LABELED_ACCOUNT_PATTERN = Pattern.compile(
            "(?im)(^|\\R)([ \\t]*(?:계정|아이디|사용자명|user(?:name)?|account)[ \\t]*[:：=][ \\t]*)[^\\s,;]+"
    );
    private static final Pattern LABELED_INTERNAL_ID_PATTERN = Pattern.compile(
            "(?im)(^|\\R)([ \\t]*(?:사번|학번|직원번호|employee[ _-]?id|student[ _-]?id)[ \\t]*[:：=][ \\t]*)[^\\s,;]+"
    );

    public String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String masked = RESIDENT_REGISTRATION_PATTERN.matcher(value).replaceAll("[RESIDENT_ID]");
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL]");
        masked = KOREAN_PHONE_PATTERN.matcher(masked).replaceAll("[PHONE]");
        masked = URL_PATTERN.matcher(masked).replaceAll("[URL]");
        masked = LABELED_NAME_PATTERN.matcher(masked).replaceAll("$1$2[NAME]");
        masked = LABELED_ADDRESS_PATTERN.matcher(masked).replaceAll("$1$2[ADDRESS]");
        masked = LABELED_ACCOUNT_PATTERN.matcher(masked).replaceAll("$1$2[ACCOUNT]");
        return LABELED_INTERNAL_ID_PATTERN.matcher(masked).replaceAll("$1$2[INTERNAL_ID]");
    }
}

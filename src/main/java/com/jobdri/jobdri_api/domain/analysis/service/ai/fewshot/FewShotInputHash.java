package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class FewShotInputHash {
    private static final Pattern NORMALIZED_INPUT_WHITESPACE_PATTERN = Pattern.compile("[\\p{Z}\\s]+");

    private FewShotInputHash() {
    }

    static String of(
            List<String> mainTasks,
            List<String> qualifications,
            String question,
            String answer
    ) {
        String normalizedMainTasks = normalizeInputSection(mainTasks == null ? "" : String.join("\n", mainTasks));
        String normalizedQualifications = normalizeInputSection(
                qualifications == null ? "" : String.join("\n", qualifications)
        );
        String normalizedQuestion = normalizeInputSection(question);
        String normalizedAnswer = normalizeInputSection(answer);
        if (normalizedMainTasks.isEmpty()
                && normalizedQualifications.isEmpty()
                && normalizedQuestion.isEmpty()
                && normalizedAnswer.isEmpty()) {
            return "";
        }
        return sha256(
                normalizedMainTasks + '\u001f'
                        + normalizedQualifications + '\u001f'
                        + normalizedQuestion + '\u001f'
                        + normalizedAnswer
        );
    }

    private static String normalizeInputSection(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String unicodeNormalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return NORMALIZED_INPUT_WHITESPACE_PATTERN.matcher(unicodeNormalized)
                .replaceAll(" ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append("%02x".formatted(b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

}

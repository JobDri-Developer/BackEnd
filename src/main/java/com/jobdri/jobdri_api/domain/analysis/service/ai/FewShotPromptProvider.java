package com.jobdri.jobdri_api.domain.analysis.service.ai;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@Slf4j
public class FewShotPromptProvider {
    private static final String RESOURCE_PATH = "ai/analysis/fewshot-prompt-block.txt";
    private static final Pattern EXAMPLE_HEADER_PATTERN = Pattern.compile("(?m)^## 예시 ");

    @Getter
    private final String prompt;

    public FewShotPromptProvider() {
        this.prompt = loadPrompt();
        log.debug(
                "few-shot prompt resource loaded. success={}, exampleCount={}",
                true,
                EXAMPLE_HEADER_PATTERN.matcher(prompt).results().count()
        );
    }

    private String loadPrompt() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (var inputStream = resource.getInputStream()) {
            String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            if (!org.springframework.util.StringUtils.hasText(content)) {
                throw new IllegalStateException("Few-shot prompt resource is empty: " + RESOURCE_PATH);
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load few-shot prompt resource: " + RESOURCE_PATH, e);
        }
    }
}

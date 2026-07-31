package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.SelectedFewShotCase;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class FewShotPromptProvider {
    private static final String RESOURCE_PATH = "ai/analysis/fewshot-prompt-block.txt";
    private static final Pattern EXAMPLE_HEADER_PATTERN = Pattern.compile("(?m)^## 예시 ");
    private static final Pattern EXAMPLE_SPLIT_PATTERN = Pattern.compile("(?m)(?=^## 예시 )");

    @Getter
    private final String prompt;
    private final String promptPreamble;
    private final List<String> fixedExampleBlocks;

    public FewShotPromptProvider() {
        this.prompt = loadPrompt();
        this.promptPreamble = extractPreamble(prompt);
        this.fixedExampleBlocks = parseExampleBlocks(prompt);
        log.debug(
                "few-shot prompt resource loaded. success={}, exampleCount={}",
                true,
                fixedExampleBlocks.size()
        );
    }

    public List<String> getFixedExampleBlocks() {
        return fixedExampleBlocks;
    }

    public String buildPromptBlock(List<SelectedFewShotCase> selectedFewShots) {
        if (selectedFewShots == null || selectedFewShots.isEmpty()) {
            return prompt;
        }
        List<String> blocks = selectedFewShots.stream()
                .filter(selected -> selected != null && selected.fewShotCase() != null)
                .map(selected -> selected.fewShotCase().promptBlock())
                .filter(org.springframework.util.StringUtils::hasText)
                .toList();
        if (blocks.isEmpty()) {
            return prompt;
        }
        return promptPreamble + "\n\n" + String.join("\n\n", blocks);
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

    private static String extractPreamble(String prompt) {
        java.util.regex.Matcher matcher = EXAMPLE_HEADER_PATTERN.matcher(prompt);
        if (!matcher.find()) {
            return prompt;
        }
        return prompt.substring(0, matcher.start()).trim();
    }

    private static List<String> parseExampleBlocks(String prompt) {
        String[] parts = EXAMPLE_SPLIT_PATTERN.split(prompt);
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String block = part.trim();
            if (block.startsWith("## 예시 ")) {
                result.add(block);
            }
        }
        return List.copyOf(result);
    }
}

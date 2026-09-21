package com.jobdri.jobdri_api.domain.analysis.dto.internal.worker;

import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotSelectionMetadata;

public record AnalysisWorkerFewShotContext(
        String promptBlock,
        FewShotSelectionMetadata selectionMetadata
) {
    public static final int MAX_PROMPT_BLOCK_LENGTH = 20_000;

    public AnalysisWorkerFewShotContext {
        if (promptBlock == null || promptBlock.isBlank()) {
            throw new IllegalArgumentException("promptBlock must not be blank");
        }
        if (promptBlock.length() > MAX_PROMPT_BLOCK_LENGTH) {
            throw new IllegalArgumentException(
                    "promptBlock exceeds max length: " + promptBlock.length() + " > " + MAX_PROMPT_BLOCK_LENGTH
            );
        }
        if (selectionMetadata == null) {
            throw new IllegalArgumentException("selectionMetadata must not be null");
        }
    }
}

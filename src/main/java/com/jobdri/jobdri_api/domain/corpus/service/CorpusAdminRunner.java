package com.jobdri.jobdri_api.domain.corpus.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Slf4j
@Component
@Profile("!analysis-eval")
@RequiredArgsConstructor
public class CorpusAdminRunner implements ApplicationRunner {

    @Value("${app.corpus.import.run-on-startup:false}")
    private boolean runImportOnStartup;

    @Value("${app.corpus.import.xlsx-path:}")
    private String importXlsxPath;

    @Value("${app.corpus.embedding.sync-on-startup:false}")
    private boolean syncEmbeddingsOnStartup;

    private final BootstrapAdminService bootstrapAdminService;
    private final CorpusImportService corpusImportService;
    private final CorpusEmbeddingSyncService corpusEmbeddingSyncService;

    @Override
    public void run(ApplicationArguments args) {
        bootstrapAdminService.promoteConfiguredAdmins();

        if (runImportOnStartup && StringUtils.hasText(importXlsxPath)) {
            try {
                CorpusImportResult result = corpusImportService.importFromXlsx(Path.of(importXlsxPath));
                log.info("corpus import 완료: {}", result);
            } catch (Exception e) {
                log.error("startup corpus import 실패. path={}", importXlsxPath, e);
            }
        }

        if (syncEmbeddingsOnStartup) {
            try {
                log.info("corpus embedding sync 완료: {}", corpusEmbeddingSyncService.syncAll(null));
            } catch (Exception e) {
                log.error("startup corpus embedding sync 실패", e);
            }
        }
    }
}

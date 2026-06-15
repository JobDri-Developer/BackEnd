package com.jobdri.jobdri_api.domain.corpus.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Slf4j
@Component
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
    public void run(ApplicationArguments args) throws Exception {
        bootstrapAdminService.promoteConfiguredAdmins();

        if (runImportOnStartup && StringUtils.hasText(importXlsxPath)) {
            CorpusImportResult result = corpusImportService.importFromXlsx(Path.of(importXlsxPath));
            log.info("corpus import 완료: {}", result);
        }

        if (syncEmbeddingsOnStartup) {
            log.info("corpus embedding sync 완료: {}", corpusEmbeddingSyncService.syncAll(null));
        }
    }
}

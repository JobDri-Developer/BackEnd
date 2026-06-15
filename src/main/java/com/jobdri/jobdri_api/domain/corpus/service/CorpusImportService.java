package com.jobdri.jobdri_api.domain.corpus.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.corpus.entity.MockJobPostingCorpus;
import com.jobdri.jobdri_api.domain.corpus.entity.MockQuestionCorpus;
import com.jobdri.jobdri_api.domain.corpus.repository.MockJobPostingCorpusRepository;
import com.jobdri.jobdri_api.domain.corpus.repository.MockQuestionCorpusRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CorpusImportService {

    private static final String JD_SHEET_NAME = "jd_embed_corpus";
    private static final String QUESTION_SHEET_NAME = "question_embed_corpus";

    private final CompanyRepository companyRepository;
    private final MockJobPostingCorpusRepository mockJobPostingCorpusRepository;
    private final MockQuestionCorpusRepository mockQuestionCorpusRepository;
    private final CorpusClassificationResolver corpusClassificationResolver;

    public CorpusImportResult importFromXlsx(Path xlsxPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(xlsxPath);
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            return importWorkbook(workbook);
        }
    }

    public CorpusImportResult importWorkbook(Workbook workbook) {
        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        ImportStats stats = new ImportStats();
        Map<String, Company> companyCache = new HashMap<>();

        importJobPostingSheet(workbook.getSheet(JD_SHEET_NAME), formatter, evaluator, stats, companyCache);
        importQuestionSheet(workbook.getSheet(QUESTION_SHEET_NAME), formatter, evaluator, stats, companyCache);

        return stats.toResult();
    }

    private void importJobPostingSheet(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            ImportStats stats,
            Map<String, Company> companyCache
    ) {
        if (sheet == null) {
            return;
        }

        Iterator<Row> rows = sheet.rowIterator();
        if (!rows.hasNext()) {
            return;
        }

        Map<String, Integer> headerMap = readHeaderMap(rows.next(), formatter, evaluator);
        validateRequiredHeaders(
                headerMap,
                "analysis_id", "company_name", "job_group_l1", "job_family_l2", "role_l3",
                "skills", "responsibilities", "requirements", "preferred", "embedding_text", "is_valid_for_embedding"
        );
        while (rows.hasNext()) {
            Row row = rows.next();
            String sourceAnalysisId = getString(row, headerMap, "analysis_id", formatter, evaluator);
            if (!StringUtils.hasText(sourceAnalysisId)) {
                continue;
            }

            String companyName = getString(row, headerMap, "company_name", formatter, evaluator);
            Company company = resolveCompany(companyName, stats, companyCache);
            Optional<DetailClassification> detailClassification = resolveClassification(row, headerMap, formatter, evaluator, stats);

            MockJobPostingCorpus corpus = mockJobPostingCorpusRepository.findBySourceAnalysisId(sourceAnalysisId)
                    .orElse(null);

            if (corpus == null) {
                mockJobPostingCorpusRepository.save(MockJobPostingCorpus.create(
                        sourceAnalysisId,
                        company,
                        detailClassification.orElse(null),
                        companyName,
                        getString(row, headerMap, "industry", formatter, evaluator),
                        getString(row, headerMap, "job_group_l1", formatter, evaluator),
                        getString(row, headerMap, "job_family_l2", formatter, evaluator),
                        getString(row, headerMap, "role_l3", formatter, evaluator),
                        getString(row, headerMap, "skills", formatter, evaluator),
                        getString(row, headerMap, "responsibilities", formatter, evaluator),
                        getString(row, headerMap, "requirements", formatter, evaluator),
                        getString(row, headerMap, "preferred", formatter, evaluator),
                        getString(row, headerMap, "embedding_text", formatter, evaluator),
                        getBoolean(row, headerMap, "is_valid_for_embedding", formatter, evaluator),
                        null
                ));
                stats.createdJobPostings++;
                continue;
            }

            corpus.updateFromImport(
                    company,
                    detailClassification.orElse(null),
                    companyName,
                    getString(row, headerMap, "industry", formatter, evaluator),
                    getString(row, headerMap, "job_group_l1", formatter, evaluator),
                    getString(row, headerMap, "job_family_l2", formatter, evaluator),
                    getString(row, headerMap, "role_l3", formatter, evaluator),
                    getString(row, headerMap, "skills", formatter, evaluator),
                    getString(row, headerMap, "responsibilities", formatter, evaluator),
                    getString(row, headerMap, "requirements", formatter, evaluator),
                    getString(row, headerMap, "preferred", formatter, evaluator),
                    getString(row, headerMap, "embedding_text", formatter, evaluator),
                    getBoolean(row, headerMap, "is_valid_for_embedding", formatter, evaluator),
                    null
            );
            stats.updatedJobPostings++;
        }
    }

    private void importQuestionSheet(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            ImportStats stats,
            Map<String, Company> companyCache
    ) {
        if (sheet == null) {
            return;
        }

        Iterator<Row> rows = sheet.rowIterator();
        if (!rows.hasNext()) {
            return;
        }

        Map<String, Integer> headerMap = readHeaderMap(rows.next(), formatter, evaluator);
        validateRequiredHeaders(
                headerMap,
                "question_id", "analysis_id", "company_name", "job_group_l1", "job_family_l2", "role_l3",
                "source", "question_text", "embedding_text", "is_valid_for_embedding"
        );
        while (rows.hasNext()) {
            Row row = rows.next();
            String sourceQuestionId = getString(row, headerMap, "question_id", formatter, evaluator);
            if (!StringUtils.hasText(sourceQuestionId)) {
                continue;
            }

            String companyName = getString(row, headerMap, "company_name", formatter, evaluator);
            Company company = resolveCompany(companyName, stats, companyCache);
            Optional<DetailClassification> detailClassification = resolveClassification(row, headerMap, formatter, evaluator, stats);

            MockQuestionCorpus corpus = mockQuestionCorpusRepository.findBySourceQuestionId(sourceQuestionId)
                    .orElse(null);

            if (corpus == null) {
                mockQuestionCorpusRepository.save(MockQuestionCorpus.create(
                        sourceQuestionId,
                        getString(row, headerMap, "analysis_id", formatter, evaluator),
                        company,
                        detailClassification.orElse(null),
                        companyName,
                        getString(row, headerMap, "job_group_l1", formatter, evaluator),
                        getString(row, headerMap, "job_family_l2", formatter, evaluator),
                        getString(row, headerMap, "role_l3", formatter, evaluator),
                        getString(row, headerMap, "source", formatter, evaluator),
                        getString(row, headerMap, "question_type", formatter, evaluator),
                        getInteger(row, headerMap, "char_limit", formatter, evaluator),
                        getString(row, headerMap, "question_text", formatter, evaluator),
                        getString(row, headerMap, "embedding_text", formatter, evaluator),
                        getBoolean(row, headerMap, "is_valid_for_embedding", formatter, evaluator)
                ));
                stats.createdQuestions++;
                continue;
            }

            corpus.updateFromImport(
                    company,
                    detailClassification.orElse(null),
                    companyName,
                    getString(row, headerMap, "job_group_l1", formatter, evaluator),
                    getString(row, headerMap, "job_family_l2", formatter, evaluator),
                    getString(row, headerMap, "role_l3", formatter, evaluator),
                    getString(row, headerMap, "source", formatter, evaluator),
                    getString(row, headerMap, "question_type", formatter, evaluator),
                    getInteger(row, headerMap, "char_limit", formatter, evaluator),
                    getString(row, headerMap, "question_text", formatter, evaluator),
                    getString(row, headerMap, "embedding_text", formatter, evaluator),
                    getBoolean(row, headerMap, "is_valid_for_embedding", formatter, evaluator)
            );
            stats.updatedQuestions++;
        }
    }

    private Optional<DetailClassification> resolveClassification(
            Row row,
            Map<String, Integer> headerMap,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            ImportStats stats
    ) {
        String jobGroupL1 = getString(row, headerMap, "job_group_l1", formatter, evaluator);
        String jobFamilyL2 = getString(row, headerMap, "job_family_l2", formatter, evaluator);
        String roleL3 = getString(row, headerMap, "role_l3", formatter, evaluator);

        Optional<DetailClassification> detailClassification = corpusClassificationResolver.resolve(
                jobGroupL1,
                jobFamilyL2,
                roleL3
        );
        if (detailClassification.isPresent()) {
            stats.matchedClassifications++;
        } else if (StringUtils.hasText(roleL3)) {
            stats.unmatchedClassifications++;
        }
        return detailClassification;
    }

    private Company resolveCompany(String companyName, ImportStats stats, Map<String, Company> companyCache) {
        String normalizedCompanyName = normalize(companyName);
        if (!StringUtils.hasText(normalizedCompanyName)) {
            return null;
        }
        Company cachedCompany = companyCache.get(normalizedCompanyName);
        if (cachedCompany != null) {
            return cachedCompany;
        }
        Company company = companyRepository.findByName(normalizedCompanyName)
                .orElseGet(() -> {
                    stats.createdCompanies++;
                    return companyRepository.save(Company.create(normalizedCompanyName, null));
                });
        companyCache.put(normalizedCompanyName, company);
        return company;
    }

    private Map<String, Integer> readHeaderMap(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> headerMap = new HashMap<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = headerRow.getCell(i);
            String value = normalize(getCellString(cell, formatter, evaluator));
            if (value != null) {
                headerMap.put(value, i);
            }
        }
        return headerMap;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerMap, String... requiredColumns) {
        for (String requiredColumn : requiredColumns) {
            if (!headerMap.containsKey(requiredColumn)) {
                throw new IllegalArgumentException("필수 헤더가 누락되었습니다. column=" + requiredColumn);
            }
        }
    }

    private String getString(
            Row row,
            Map<String, Integer> headerMap,
            String columnName,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        Integer index = headerMap.get(columnName);
        if (index == null) {
            return null;
        }
        return normalize(getCellString(row.getCell(index), formatter, evaluator));
    }

    private Integer getInteger(
            Row row,
            Map<String, Integer> headerMap,
            String columnName,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        String value = getString(row, headerMap, columnName, formatter, evaluator);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replace(",", "").trim();
        try {
            if (normalized.contains(".")) {
                double decimalValue = Double.parseDouble(normalized);
                return (int) Math.round(decimalValue);
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            try {
                Number parsed = DecimalFormat.getInstance().parse(normalized);
                return parsed == null ? null : parsed.intValue();
            } catch (ParseException ignored) {
                return null;
            }
        }
    }

    private boolean getBoolean(
            Row row,
            Map<String, Integer> headerMap,
            String columnName,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        String value = getString(row, headerMap, columnName, formatter, evaluator);
        return Boolean.parseBoolean(value);
    }

    private String getCellString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        return formatter.formatCellValue(cell, evaluator);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static final class ImportStats {
        private int createdCompanies;
        private int createdJobPostings;
        private int updatedJobPostings;
        private int createdQuestions;
        private int updatedQuestions;
        private int matchedClassifications;
        private int unmatchedClassifications;

        private CorpusImportResult toResult() {
            return new CorpusImportResult(
                    createdCompanies,
                    createdJobPostings,
                    updatedJobPostings,
                    createdQuestions,
                    updatedQuestions,
                    matchedClassifications,
                    unmatchedClassifications
            );
        }
    }
}

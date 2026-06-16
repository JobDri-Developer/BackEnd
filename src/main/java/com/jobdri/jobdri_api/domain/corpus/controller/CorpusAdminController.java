package com.jobdri.jobdri_api.domain.corpus.controller;

import com.jobdri.jobdri_api.domain.corpus.dto.request.CorpusEmbeddingSyncRequest;
import com.jobdri.jobdri_api.domain.corpus.dto.request.CorpusImportRequest;
import com.jobdri.jobdri_api.domain.corpus.dto.response.CorpusEmbeddingSyncResponse;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusEmbeddingSyncService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusImportResult;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusImportService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/corpus")
@Tag(name = "CorpusAdmin", description = "관리자용 corpus 적재/임베딩 API")
public class CorpusAdminController {

    private final CorpusImportService corpusImportService;
    private final CorpusEmbeddingSyncService corpusEmbeddingSyncService;

    @Value("${app.corpus.import.allowed-root:}")
    private String allowedImportRoot;

    @Operation(summary = "corpus 엑셀 적재", description = "관리자가 xlsx 파일 경로를 넘겨 corpus 원본 테이블에 적재합니다.")
    @PostMapping("/import")
    public ApiResponse<CorpusImportResult> importCorpus(
            @Valid @RequestBody CorpusImportRequest request
    ) throws IOException {
        Path validatedPath = validateImportPath(request.filePath());
        return ApiResponse.onSuccess(
                "corpus 엑셀 적재에 성공했습니다.",
                corpusImportService.importFromXlsx(validatedPath)
        );
    }

    @Operation(summary = "corpus 임베딩 동기화", description = "유효한 corpus 데이터를 읽어 pgvector 테이블에 임베딩을 저장합니다.")
    @PostMapping("/embeddings/sync")
    public ApiResponse<CorpusEmbeddingSyncResponse> syncEmbeddings(
            @Valid @RequestBody CorpusEmbeddingSyncRequest request
    ) {
        return ApiResponse.onSuccess(
                "corpus 임베딩 동기화에 성공했습니다.",
                corpusEmbeddingSyncService.syncAll(request.limit())
        );
    }

    private Path validateImportPath(String rawPath) {
        if (allowedImportRoot == null || allowedImportRoot.isBlank()) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "corpus import 허용 경로가 설정되지 않았습니다."
            );
        }

        try {
            Path requestedPath = Path.of(rawPath).toAbsolutePath().normalize();
            Path allowedRootPath = Path.of(allowedImportRoot).toAbsolutePath().normalize().toRealPath();
            Path resolvedPath = requestedPath.toRealPath();
            if (!resolvedPath.startsWith(allowedRootPath)) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "허용된 import 경로 밖의 파일에는 접근할 수 없습니다."
                );
            }
            return resolvedPath;
        } catch (InvalidPathException e) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "유효하지 않은 파일 경로입니다."
            );
        } catch (IOException e) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "접근 가능한 import 파일 경로가 아닙니다."
            );
        }
    }
}

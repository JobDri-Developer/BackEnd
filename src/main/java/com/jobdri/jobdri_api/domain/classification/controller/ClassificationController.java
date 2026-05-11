package com.jobdri.jobdri_api.domain.classification.controller;

import com.jobdri.jobdri_api.domain.classification.dto.response.ClassificationLowerResponseDto;
import com.jobdri.jobdri_api.domain.classification.dto.response.ClassificationResponseDto;
import com.jobdri.jobdri_api.domain.classification.service.ClassificationService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/classifications")
@Tag(name = "Classification", description = "직무 분류 조회 API")
public class ClassificationController {

    private final ClassificationService classificationService;

    @Operation(
            summary = "대분류 전체 조회",
            description = "등록된 모든 대분류(Big Classification)를 전체 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대분류 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"대분류 조회에 성공했습니다.\",\"result\":[{\"classificationId\":1,\"classificationName\":\"개발\"},{\"classificationId\":2,\"classificationName\":\"디자인\"}],\"error\":null}")
                    )
            )
    })
    @GetMapping
    public ApiResponse<List<ClassificationResponseDto>> getBigClassifications() {
        return ApiResponse.onSuccess(
                "대분류 조회에 성공했습니다.",
                classificationService.getBigClassifications()
        );
    }

    @Operation(
            summary = "대분류 기준 중분류 조회",
            description = "대분류 ID를 기준으로 연결된 모든 중분류(Middle Classification)를 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "중분류 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"중분류 조회에 성공했습니다.\",\"result\":{\"upperClassId\":1,\"upperClassName\":\"개발\",\"lowerClassifications\":[{\"classificationId\":10,\"classificationName\":\"백엔드\"},{\"classificationId\":11,\"classificationName\":\"프론트엔드\"}]},\"error\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "대분류를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"CLASSIFICATION_4041\",\"message\":\"분류를 찾을 수 없습니다.\",\"result\":null,\"error\":\"해당 대분류를 찾을 수 없습니다. bigId=999\"}")
                    )
            )
    })
    @GetMapping("/{bigId}/middles")
    public ApiResponse<ClassificationLowerResponseDto> getMiddleClassifications(@PathVariable Long bigId) {
        return ApiResponse.onSuccess(
                "중분류 조회에 성공했습니다.",
                classificationService.getMiddleClassifications(bigId)
        );
    }

    @Operation(
            summary = "중분류 기준 소분류 조회",
            description = "중분류 ID를 기준으로 연결된 모든 소분류(Detail Classification)를 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "소분류 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":true,\"code\":\"COMMON2000\",\"message\":\"소분류 조회에 성공했습니다.\",\"result\":{\"upperClassId\":10,\"upperClassName\":\"백엔드\",\"lowerClassifications\":[{\"classificationId\":100,\"classificationName\":\"Java/Spring\"},{\"classificationId\":101,\"classificationName\":\"Node.js\"}]},\"error\":null}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "중분류를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"isSuccess\":false,\"code\":\"CLASSIFICATION_4041\",\"message\":\"분류를 찾을 수 없습니다.\",\"result\":null,\"error\":\"해당 중분류를 찾을 수 없습니다. middleId=999\"}")
                    )
            )
    })
    @GetMapping("/middles/{middleId}/details")
    public ApiResponse<ClassificationLowerResponseDto> getDetailClassifications(@PathVariable Long middleId) {
        return ApiResponse.onSuccess(
                "소분류 조회에 성공했습니다.",
                classificationService.getDetailClassifications(middleId)
        );
    }
}

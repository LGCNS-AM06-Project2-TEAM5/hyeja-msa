package com.hyeja.engagementservice.favorite.controller;

import com.hyeja.engagementservice.favorite.dto.FavoriteResponseDTO.FavoriteItemDTO;
import com.hyeja.engagementservice.favorite.dto.FavoriteResponseDTO.FavoriteListDTO;
import com.hyeja.engagementservice.favorite.service.FavoriteService;
import com.hyeja.engagementservice.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관심 정책", description = "회원 관심 정책 API")
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(
            summary = "관심 정책 목록 조회",
            description = "회원이 관심 정책으로 등록한 정책을 최근 등록순으로 8개씩 조회합니다. "
                    + "keyword가 있으면 정책명 또는 지원 내용으로 검색합니다. "
                    + "더보기 요청 시 page 값을 1씩 증가시키며, 관심 정책이 없으면 빈 목록을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "관심 정책 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "page가 0 미만이거나 size가 0 이하 (COMMON_001)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "회원 식별 헤더 누락 또는 오류 (COMMON_002)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("")
    public ApiResponse<FavoriteListDTO> getMyFavorites(
            @RequestHeader("X-Member-Id") Long memberId,
            @Parameter(
                    name = "keyword",
                    description = "정책명 또는 지원 내용 검색어. 생략하거나 공백이면 전체 목록을 조회합니다.",
                    in = ParameterIn.QUERY,
                    example = "월세"
            )
            @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(
                    name = "page",
                    description = "페이지 번호(0부터 시작)",
                    in = ParameterIn.QUERY,
                    example = "0"
            )
            @RequestParam(name = "page", defaultValue = "0")
            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @Parameter(
                    name = "size",
                    description = "페이지당 관심 정책 개수",
                    in = ParameterIn.QUERY,
                    example = "8"
            )
            @RequestParam(name = "size", defaultValue = "8")
            @Positive(message = "페이지 크기는 양수여야 합니다.") int size
    ) {
        FavoriteListDTO result = favoriteService.getMyFavorites(memberId, keyword, page, size);
        return ApiResponse.onSuccess(result);
    }

    @Operation(
            summary = "관심 정책 등록",
            description = "로컬 읽기 모델에 있는 유효한 정책을 관심 정책으로 등록합니다. "
                    + "같은 정책을 중복 등록할 수 없습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "관심 정책 등록 성공 (SUCCESS_001)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "로컬에 없거나 유효하지 않은 정책 (POLICY_001)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 등록된 관심 정책 (FAVORITE_001)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping("/{policyId}")
    public ApiResponse<FavoriteItemDTO> createFavorite(
            @Parameter(
                    name = "policyId",
                    description = "등록할 정책 ID",
                    in = ParameterIn.PATH,
                    example = "R202609230001",
                    required = true
            )
            @PathVariable(name = "policyId") String policyId,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        FavoriteItemDTO result = favoriteService.createFavorite(memberId, policyId);
        return ApiResponse.onSuccess(result);
    }

    @Operation(
            summary = "관심 정책 삭제",
            description = "회원이 등록한 관심 정책을 데이터베이스에서 영구 삭제합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "관심 정책 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "정책 ID 누락",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "로컬에 존재하지 않는 정책 (POLICY_001), "
                            + "등록되지 않은 관심 정책 (FAVORITE_002)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @DeleteMapping("/{policyId}")
    public ApiResponse<Void> deleteFavorite(
            @Parameter(
                    name = "policyId",
                    description = "삭제할 관심 정책의 정책 ID",
                    in = ParameterIn.PATH,
                    example = "R202609230001",
                    required = true
            )
            @PathVariable(name = "policyId")
            @NotBlank(message = "정책 ID는 필수입니다.") String policyId,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        favoriteService.deleteFavorite(memberId, policyId);
        return ApiResponse.onSuccess(null);
    }
}

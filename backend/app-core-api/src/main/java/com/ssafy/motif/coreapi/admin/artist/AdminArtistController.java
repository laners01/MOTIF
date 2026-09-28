package com.ssafy.motif.coreapi.admin.artist;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateRequest;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistCreateResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistDeleteResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistDetailResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistSummaryResponse;
import com.ssafy.motif.coreapi.admin.artist.dto.ArtistUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 아티스트 API (어드민 명세 §3.5). 삭제(§3.5.5)는 에셋·가격 연쇄를 함께 돌린다. */
@Tag(name = "Admin Artist", description = "아티스트 관리 (어드민 명세 §3.5)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/artists")
public class AdminArtistController {

    private final AdminArtistService adminArtistService;

    @Operation(summary = "아티스트 목록",
            description = "이름 부분 일치·그룹·소속사 필터. 최신 등록순 offset 페이지.")
    @GetMapping
    public ApiResponse<PageResponse<ArtistSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer groupSeq,
            @RequestParam(required = false) Integer companySeq,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                adminArtistService.list(q, groupSeq, companySeq, includeDeleted, page, size));
    }

    @Operation(summary = "아티스트 등록",
            description = "users(ARTIST) 와 artists 를 한 트랜잭션에 만든다. "
                    + "그룹 지정 시 companySeq 는 그 그룹의 소속사여야 한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistCreateResponse> create(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                    @Valid @RequestBody ArtistCreateRequest request) {
        return ApiResponse.success(adminArtistService.create(admin, request));
    }

    @Operation(summary = "아티스트 상세", description = "패스 가격표와 최신 챌린지 5건을 함께 준다.")
    @GetMapping("/{artistSeq}")
    public ApiResponse<ArtistDetailResponse> detail(@PathVariable int artistSeq) {
        return ApiResponse.success(adminArtistService.detail(artistSeq));
    }

    @Operation(summary = "아티스트 수정",
            description = "부분 수정. groupSeq 를 null 로 보내면 솔로 전환, 키를 생략하면 소속 그대로다.")
    @PatchMapping("/{artistSeq}")
    public ApiResponse<ArtistDetailResponse> update(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                    @PathVariable int artistSeq,
                                                    @Valid @RequestBody ArtistUpdateRequest request) {
        return ApiResponse.success(adminArtistService.update(admin, artistSeq, request));
    }

    @Operation(summary = "아티스트 삭제",
            description = "확인 대화상자 뒤 호출. 소프트 삭제 + 연쇄 — artists·users 의 is_deleted 와 "
                    + "에셋·에셋 가격·패스 가격의 is_active 를 한 트랜잭션에 내린다. "
                    + "data 의 세 건수는 이번 삭제로 실제 꺼진 행 수다. 이미 삭제된 아티스트는 404.")
    @DeleteMapping("/{artistSeq}")
    public ApiResponse<ArtistDeleteResponse> delete(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                    @PathVariable int artistSeq) {
        return ApiResponse.success(adminArtistService.delete(admin, artistSeq));
    }
}

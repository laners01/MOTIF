package com.ssafy.motif.coreapi.admin.artist;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasCreateResponse;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasRequest;
import com.ssafy.motif.coreapi.admin.alias.dto.AliasResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 어드민 아티스트 별칭 API (어드민 명세 §3.5.6~9) — 팬 앱 검색(API §5.2)이 읽는 별칭을
 * 채우는 경로다.
 *
 * <p><b>{@link AdminArtistController} 와 나눠 둔다.</b> 경로 접두사는 같지만 별칭은 §3.5.1~5
 * 와 트랜잭션도 검증도 겹치지 않는다. 한 클래스에 넣으면 이미 긴 아티스트 컨트롤러가 더
 * 길어지고, 별칭만 고칠 때도 아티스트 등록 경로를 지나가야 한다.
 *
 * <p>요청 DTO 는 그룹 별칭(§3.4.5~8)과 공유한다 — {@code admin/alias/dto}.
 */
@Tag(name = "Admin Artist Alias", description = "아티스트 별칭 관리 (어드민 명세 §3.5.6~9)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/artists/{artistSeq}/aliases")
public class AdminArtistAliasController {

    private final AdminArtistAliasService adminArtistAliasService;

    @Operation(summary = "별칭 목록",
            description = "비페이지 전량. 삭제된 아티스트는 404 다.")
    @GetMapping
    public ApiResponse<ListResponse<AliasResponse>> list(@PathVariable int artistSeq) {
        return ApiResponse.success(adminArtistAliasService.list(artistSeq));
    }

    @Operation(summary = "별칭 등록",
            description = "같은 언어에 여러 개를 달 수 있고 중복도 막지 않는다 — DB 에 UNIQUE 가 없다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AliasCreateResponse> create(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable int artistSeq,
            @Valid @RequestBody AliasRequest request) {
        return ApiResponse.success(adminArtistAliasService.create(admin, artistSeq, request));
    }

    @Operation(summary = "별칭 수정",
            description = "부분 수정이 아니라 전체 치환이라 PATCH 가 아닌 PUT 이다. "
                    + "경로의 아티스트 소유가 아니면 404 ALIAS_NOT_FOUND.")
    @PutMapping("/{aliasSeq}")
    public ApiResponse<AliasResponse> update(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                             @PathVariable int artistSeq,
                                             @PathVariable int aliasSeq,
                                             @Valid @RequestBody AliasRequest request) {
        return ApiResponse.success(
                adminArtistAliasService.update(admin, artistSeq, aliasSeq, request));
    }

    @Operation(summary = "별칭 삭제",
            description = "하드 삭제다. 아티스트 소프트 삭제(§3.5.5)는 별칭을 건드리지 않는다 — 그건 별개 동작이다.")
    @DeleteMapping("/{aliasSeq}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                    @PathVariable int artistSeq,
                                    @PathVariable int aliasSeq) {
        adminArtistAliasService.delete(admin, artistSeq, aliasSeq);
        return ApiResponse.success();
    }
}

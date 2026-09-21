package com.ssafy.motif.coreapi.admin.org;

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
 * 어드민 그룹 별칭 API (어드민 명세 §3.4.5~8).
 *
 * <p>아티스트 별칭(§3.5.6~9)과 계약이 같다 — 경로의 대상만 다르다. 한 컨트롤러에 두 대상을
 * 담지 않는 것은 경로가 컨트롤러를 정하는 레포 관례 때문이다.
 */
@Tag(name = "Admin Group Alias", description = "그룹 별칭 관리 (어드민 명세 §3.4.5~8)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/groups/{groupSeq}/aliases")
public class AdminGroupAliasController {

    private final AdminGroupAliasService adminGroupAliasService;

    @Operation(summary = "그룹 별칭 목록", description = "비페이지 전량. 삭제된 그룹은 404 다.")
    @GetMapping
    public ApiResponse<ListResponse<AliasResponse>> list(@PathVariable int groupSeq) {
        return ApiResponse.success(adminGroupAliasService.list(groupSeq));
    }

    @Operation(summary = "그룹 별칭 등록",
            description = "이 별칭에 검색어가 걸리면 §5.2 가 그룹의 비삭제 멤버 전원을 결과에 펼친다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AliasCreateResponse> create(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable int groupSeq,
            @Valid @RequestBody AliasRequest request) {
        return ApiResponse.success(adminGroupAliasService.create(admin, groupSeq, request));
    }

    @Operation(summary = "그룹 별칭 수정",
            description = "전체 치환(PUT). 경로의 그룹 소유가 아니면 404 ALIAS_NOT_FOUND.")
    @PutMapping("/{aliasSeq}")
    public ApiResponse<AliasResponse> update(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                             @PathVariable int groupSeq,
                                             @PathVariable int aliasSeq,
                                             @Valid @RequestBody AliasRequest request) {
        return ApiResponse.success(
                adminGroupAliasService.update(admin, groupSeq, aliasSeq, request));
    }

    @Operation(summary = "그룹 별칭 삭제",
            description = "하드 삭제다. 그룹 소프트 삭제(§3.4.4)는 별칭을 건드리지 않는다.")
    @DeleteMapping("/{aliasSeq}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                    @PathVariable int groupSeq,
                                    @PathVariable int aliasSeq) {
        adminGroupAliasService.delete(admin, groupSeq, aliasSeq);
        return ApiResponse.success();
    }
}

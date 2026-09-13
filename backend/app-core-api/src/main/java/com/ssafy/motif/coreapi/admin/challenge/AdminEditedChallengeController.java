package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 편집본 키 교체 (어드민 명세 §3.7.6).
 *
 * <p>경로가 {@code /challenges} 아래가 아니라 {@code /edited-challenges/{seq}} 라
 * 컨트롤러를 따로 둔다 — 명세가 편집본을 챌린지 하위가 아닌 독립 리소스로 적었다.
 * 로직은 {@link AdminChallengeService} 가 갖는다(같은 도메인·같은 검증).
 */
@Tag(name = "Admin Challenge", description = "챌린지 등록·관리 (어드민 명세 §3.7)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/edited-challenges")
public class AdminEditedChallengeController {

    private final AdminChallengeService adminChallengeService;

    @Operation(summary = "편집본 키 교체",
            description = "같은 행의 키만 갱신한다(조합 유지). 새 키 HEAD·길이 재검증을 하며, "
                    + "합성 잡이 참조 중이어도 허용된다 — 결과물은 별도 오브젝트다.")
    @PatchMapping("/{editedChallengeSeq}")
    public ApiResponse<EditedChallengeResponse> replaceKeys(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable Integer editedChallengeSeq,
            @Valid @RequestBody EditedChallengeUpdateRequest request) {
        return ApiResponse.success(
                adminChallengeService.replaceEditedKeys(admin, editedChallengeSeq, request));
    }
}

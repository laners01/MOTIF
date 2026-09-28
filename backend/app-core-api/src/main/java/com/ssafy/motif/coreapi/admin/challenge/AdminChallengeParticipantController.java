package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantEditedResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantEditedUpdateRequest;
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
 * 슬롯 편집본 키 교체 (어드민 명세 §3.7.6).
 *
 * <p>경로가 {@code /challenges} 아래가 아니라 {@code /challenge-participants/{seq}} 라
 * 컨트롤러를 따로 둔다 — 명세가 편집본을 챌린지 하위가 아닌 독립 리소스로 적었고,
 * 0019 에서 그 리소스가 {@code edited-challenges} 에서 슬롯으로 바뀌었다.
 * 로직은 {@link AdminChallengeService} 가 갖는다(같은 도메인·같은 검증).
 */
@Tag(name = "Admin Challenge", description = "챌린지 등록·관리 (어드민 명세 §3.7)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/challenge-participants")
public class AdminChallengeParticipantController {

    private final AdminChallengeService adminChallengeService;

    @Operation(summary = "슬롯 편집본 키 교체",
            description = "같은 행의 편집본 키만 갱신한다(인물·순서 유지). "
                    + "편집본이 들어오는 유일한 입구다 — 키가 private/edited/{challengeSeq}/"
                    + "{challengeParticipantSeq}/ 아래여야 하고, 아니면 400 이다. "
                    + "새 키 HEAD·길이 재검증을 하며, "
                    + "합성 잡이 참조 중이어도 허용된다 — 결과물은 별도 오브젝트다. "
                    + "편집본이 없던 슬롯을 채울 때는 editedChallengeKey·initialSceneKey 를 함께 보낸다.")
    @PatchMapping("/{challengeParticipantSeq}/edited")
    public ApiResponse<ParticipantEditedResponse> replaceEditedKeys(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable Integer challengeParticipantSeq,
            @Valid @RequestBody ParticipantEditedUpdateRequest request) {
        return ApiResponse.success(
                adminChallengeService.replaceEditedKeys(admin, challengeParticipantSeq, request));
    }
}

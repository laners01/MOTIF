package com.ssafy.motif.coreapi.domain.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * §7.5 슬롯 목록 항목 — 촬영 전 "함께할 아티스트" 선택 화면(시안 10-1~3).
 *
 * <p><b>영상이 없다.</b> 편집본 영상은 고른 뒤에 §7.6 으로 따로 받는다 — 화면이 갈리고,
 * 서명 URL 은 만료가 있어 필요한 시점에 받아야 한다.
 *
 * <p><b>{@code participantMask} 가 없다</b>(0020). 슬롯을 가리키는 값은
 * {@code challengeParticipantSeq} 하나다 — 앱이 비트 연산을 할 일이 사라졌다. 마스크는
 * AI 접수 페이로드 안에만 남고 서버가 파생한다.
 *
 * @param slotOrder         화면 좌→우 순서, 0부터. 배열도 이 순서다
 * @param initialSceneUrl   촬영 가이드 첫 장면. <b>{@code null} 이면 이 슬롯은 고를 수 없다</b> —
 *                          DB CHECK 가 편집본 영상과 짝을 강제하므로 이 값의 유무가 곧
 *                          "편집본이 있는가" 다. 화면은 이걸로 슬롯을 잠근다
 * @param previewImageUrl   이 슬롯을 골랐을 때 썸네일 자리에 보이는 이미지. {@code null} 이면
 *                          챌린지 {@code thumbnailUrl} 로 폴백한다
 */
@Schema(description = "챌린지 슬롯 (촬영 선택 화면)")
public record ChallengeParticipantItem(

        @Schema(description = "슬롯 식별자 — §7.6·§13.1 에 그대로 보낸다", example = "31")
        Integer challengeParticipantSeq,

        Integer artistSeq,

        @Schema(description = "아티스트 표시 이름 (users.nickname)", example = "윈터")
        String name,

        @Schema(description = "화면 좌→우 순서, 0부터", example = "0")
        int slotOrder,

        @Schema(description = "프로필 이미지 URL. 미등록이면 null", nullable = true)
        String profileImageUrl,

        @Schema(description = "선택 화면에서 프로필 원이 앉는 자리. 어드민이 아직 안 찍은 옛 슬롯은 null — 원을 숨긴다",
                nullable = true)
        ChallengeDetailResponse.Anchor anchor,

        @Schema(description = "촬영 가이드 첫 장면(서명 URL). null 이면 편집본이 없어 고를 수 없는 슬롯이다",
                nullable = true)
        String initialSceneUrl,

        @Schema(description = "이 슬롯을 골랐을 때 보이는 이미지(서명 URL). null 이면 챌린지 썸네일로 폴백",
                nullable = true)
        String previewImageUrl
) {
}

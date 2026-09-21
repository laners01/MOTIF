package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.AnchorRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 슬롯 규칙 (어드민 명세 §3.7.2 검증 2항) — S15P21D104-559, 0019 개정.
 *
 * <p><b>편집본은 슬롯 1개당 1개다</b>(0019). 예전에는 "편집본은 0개이거나 정확히 2ⁿ−1 개"
 * 라는 조합 완결성 불변식이 여기 있었는데, 사용자가 고르는 것이 <b>함께할 아티스트 한 명</b>
 * 으로 정리되면서 조합 자체가 없어졌다. 편집본은 이제 슬롯 행에 같이 실린다
 * ({@link com.ssafy.motif.domain.core.challenge.ChallengeParticipant}).
 *
 * <p><b>순서는 서버가 정한다.</b> 슬롯은 요청 배열 순서(화면 좌→우)대로 {@code slotOrder = index}
 * 를 받고, AI 접수 페이로드의 마스크는 거기서 {@code 1 << slotOrder} 로 파생된다. 어드민이
 * 비트값이나 순서를 손으로 넣지 않으므로 인물과 자리가 어긋날 수 없다.
 *
 * <p>{@link com.ssafy.motif.coreapi.admin.media.VideoRules} 와 같은 자리의 규칙 모음이고
 * 같은 성질이다 — <b>순수 계산만 한다</b>(I/O·DB 없음). 그래서 DB 없는 CI 에서 도는
 * 단위 테스트로 불변식을 고정할 수 있다(563).
 */
public final class SlotRules {

    /**
     * 슬롯 하드 상한 (§ADMIN-06 기본안, 운영 권고는 ≤ 3).
     *
     * <p>설정값으로 빼지 않는다 — 튜닝 노브가 아니라 한 챌린지에서 교체 자리를 몇 개까지
     * 두느냐는 기획 결정이고, 화면(선택 화면의 프로필 원)과 AI팀 산출물이 함께 움직여야
     * 바뀌는 값이다. {@code ck_challenge_participants_slot_order} 가 DB 에서도 같은 값을
     * 강제하므로 여기만 고치면 INSERT 가 거부된다.
     *
     * <p>0019 이전의 근거("AI팀이 {@code 2⁴−1 = 15} 개를 만들어야 한다")는 조합 편집본과
     * 함께 없어졌다 — 지금 편집본 수는 슬롯 수와 같아 상한에 선형으로만 늘어난다.
     */
    public static final int MAX_SLOTS = 4;

    private SlotRules() {
    }

    /**
     * 슬롯 {@code slotOrder} 의 AI 페이로드 마스크 — <b>단일 비트</b>다.
     *
     * <p>저장하지 않고 파생한다(AI 서버 명세 §3.2). 엔티티 쪽 짝은
     * {@link com.ssafy.motif.domain.core.challenge.ChallengeParticipant#participantMask()} 다.
     */
    public static int participantMask(int slotOrder) {
        return 1 << slotOrder;
    }

    /** 마스크(단일 비트) → 슬롯 순서. §13.1 이 앱이 보낸 마스크를 슬롯으로 되돌릴 때 쓴다. */
    public static int slotOrderOf(int singleBitMask) {
        return Integer.numberOfTrailingZeros(singleBitMask);
    }

    /** 단일 비트인가 — {@code 0} 과 조합 마스크를 모두 거른다. §13.1 의 입력 검증이다. */
    public static boolean isSingleSlotMask(int mask) {
        return mask > 0 && (mask & (mask - 1)) == 0;
    }

    /**
     * 슬롯 검증 (§3.7.2 검증 2항) — 위반을 <b>전부 모아</b> 한 번에 던진다.
     *
     * <p>{@code data} 에 위반 필드 배열을 싣는다(§3.7 실패표 "data 필드 배열").
     * 한 건 찾고 멈추면 어드민이 슬롯 4개를 네 번 제출해야 한다.
     *
     * <p><b>메인 아티스트도 슬롯이 될 수 있다.</b> 둘은 다른 층이다 — 메인
     * ({@code challenges.artist_seq})은 <b>이 챌린지가 누구 것인가</b>(목록·검색·랭킹
     * 귀속)이고, 슬롯은 <b>영상 속 어느 자리를 사용자가 가져갈 수 있는가</b>다. 2인
     * 챌린지에서 둘 다 교체 대상인 화면(§7.2 선택 화면)이 정상이므로, 메인을 슬롯에서
     * 빼면 그 사람 자리를 아무도 고를 수 없다.
     *
     * <p>중복({@code DUPLICATE_ARTIST})은 그대로 막는다 — 같은 사람이 슬롯 둘을
     * 차지하면 자리와 인물이 1:1 로 맞지 않는다.
     *
     * <p><b>편집본 키는 짝이어야 한다</b>({@code EDITED_KEYS_INCOMPLETE}) — 영상만 있고 첫
     * 장면이 없으면 DB CHECK({@code ck_challenge_participants_edited})가 거부한다. 둘 다
     * 비어 있는 것은 정상이다: 편집본을 아직 안 만든 슬롯이고, 사용자가 그 슬롯을 고르면
     * §13.1 이 막는다.
     */
    public static void checkParticipants(List<ParticipantRequest> participants) {
        List<String> violations = new ArrayList<>();
        if (participants.size() > MAX_SLOTS) {
            violations.add("SLOT_COUNT_EXCEEDED");
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (int i = 0; i < participants.size(); i++) {
            ParticipantRequest p = participants.get(i);
            if (!seen.add(p.artistSeq())) {
                violations.add("DUPLICATE_ARTIST[" + i + "]");
            }
            if (!validAnchor(p.anchor())) {
                violations.add("ANCHOR_INVALID[" + i + "]");
            }
            if (blank(p.editedChallengeKey()) != blank(p.initialSceneKey())) {
                violations.add("EDITED_KEYS_INCOMPLETE[" + i + "]");
            }
            // 편집본 없이 선택 화면 이미지만 올리면 화면이 고를 수 없는 그림이 남는다
            if (blank(p.editedChallengeKey()) && !blank(p.previewImageKey())) {
                violations.add("PREVIEW_WITHOUT_EDITED[" + i + "]");
            }
        }
        if (!violations.isEmpty()) {
            throw new BusinessException(AdminErrorCode.PARTICIPANT_INVALID,
                    "슬롯 규칙 위반: " + violations, violations);
        }
    }

    private static boolean blank(String key) {
        return key == null || key.isBlank();
    }

    /**
     * 앵커 규칙 — 중심은 썸네일 안({@code 0~1}), 반지름은 {@code 0 < r ≤ 0.5}(가로의 절반이
     * 원이 들어갈 수 있는 최대). DB CHECK({@code ck_challenge_participants_anchor})와 같은 조건이다.
     * §3.7.9 앵커 단독 수정도 이 검사를 탄다.
     */
    public static boolean validAnchor(AnchorRequest a) {
        return a.x() >= 0 && a.x() <= 1
                && a.y() >= 0 && a.y() <= 1
                && a.r() > 0 && a.r() <= 0.5;
    }
}

package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.AnchorRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 슬롯·마스크 규칙 (어드민 명세 §3.7.2 검증 2·3항) — S15P21D104-559.
 *
 * <p><b>챌린지 등록의 핵심 불변식은 하나다 — 편집본은 0개이거나 정확히 2ⁿ−1 개다.</b>
 * 그 사이(부분 집합)는 없다.
 * 사용자는 촬영 전에 "누구를 뺄지"를 고르고, 서버는 그 조합의 편집본을 꺼내 준다.
 * 조합 하나가 비면 그 사용자는 <b>촬영 직전에</b> {@code EDITED_CHALLENGE_NOT_FOUND} 를
 * 만난다(API 명세 §13.1 주석). 그래서 등록 시점에 전조합을 강제한다.
 *
 * <p><b>마스크는 서버가 계산한다.</b> 슬롯은 요청 배열 순서(화면 좌→우)대로
 * {@code 1 << index}, 편집본은 {@code removedSlots} 인덱스 집합을 {@code Σ 2^idx} 로
 * 환산한다. 어드민이 비트값을 손으로 넣지 않으므로 오타로 조합이 어긋날 수 없다.
 *
 * <p>{@link com.ssafy.motif.coreapi.admin.media.VideoRules} 와 같은 자리의 규칙 모음이고
 * 같은 성질이다 — <b>순수 계산만 한다</b>(I/O·DB 없음). 그래서 DB 없는 CI 에서 도는
 * 단위 테스트로 불변식을 고정할 수 있다(563).
 */
public final class SlotRules {

    /**
     * 슬롯 하드 상한 (§ADMIN-06 기본안, 운영 권고는 ≤ 3).
     *
     * <p>설정값으로 빼지 않는다 — 튜닝 노브가 아니라 AI팀이 만들어야 하는 편집본
     * 개수({@code 2⁴−1 = 15})가 정하는 한계다. 바꾸려면 그 파이프라인이 먼저 바뀐다.
     */
    public static final int MAX_SLOTS = 4;

    private SlotRules() {
    }

    /** 슬롯 i 의 {@code participant_mask} — 단일 비트다(DB CHECK). */
    public static int participantMask(int index) {
        return 1 << index;
    }

    /**
     * 슬롯 검증 (§3.7.2 검증 2항) — 위반을 <b>전부 모아</b> 한 번에 던진다.
     *
     * <p>{@code data} 에 위반 필드 배열을 싣는다(§3.7 실패표 "data 필드 배열").
     * 한 건 찾고 멈추면 어드민이 슬롯 4개를 네 번 제출해야 한다.
     */
    public static void checkParticipants(Integer mainArtistSeq, List<ParticipantRequest> participants) {
        List<String> violations = new ArrayList<>();
        if (participants.size() > MAX_SLOTS) {
            violations.add("SLOT_COUNT_EXCEEDED");
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (int i = 0; i < participants.size(); i++) {
            ParticipantRequest p = participants.get(i);
            if (p.artistSeq().equals(mainArtistSeq)) {
                violations.add("MAIN_ARTIST_IN_SLOT[" + i + "]");
            }
            if (!seen.add(p.artistSeq())) {
                violations.add("DUPLICATE_ARTIST[" + i + "]");
            }
            if (!validAnchor(p.anchor())) {
                violations.add("ANCHOR_INVALID[" + i + "]");
            }
        }
        if (!violations.isEmpty()) {
            throw new BusinessException(AdminErrorCode.PARTICIPANT_INVALID,
                    "슬롯 규칙 위반: " + violations, violations);
        }
    }

    /**
     * 편집본 마스크 계산 + 전조합 완결성 검증 (§3.7.2 검증 3항).
     *
     * <p>실패는 409 {@code EDITED_SET_INCOMPLETE} 이고 {@code data} 는 명세가 정한
     * {@code {expected, missingMasks, duplicateMasks, unknownMasks}} 다 — 화면이 매트릭스에
     * 어느 칸이 비었는지 바로 표시한다.
     *
     * @return 요청 순서 그대로의 마스크 목록 — 편집본 행을 만들 때 짝으로 쓴다
     */
    public static List<Integer> editedMasks(int slotCount, List<EditedChallengeRequest> editedChallenges) {
        int expected = (1 << slotCount) - 1;

        // 편집본 0개는 허용한다 — "아직 촬영은 안 되는, 표시 전용 챌린지"다. 슬롯만 있으면
        // 설명 시트의 참여자 아바타가 뜨고, 영상 재생에는 편집본이 쓰이지 않는다.
        // 촬영을 시도하면 API 명세 §13.1 이 정의한 EDITED_CHALLENGE_NOT_FOUND 로 막힌다.
        //
        // 막는 것은 여전히 **부분 집합**이다(7개 중 3개). 그쪽이 진짜 위험하다 —
        // 목록에서는 완성된 것처럼 보이는데 특정 조합을 고른 사용자만 촬영 직전에 404 를
        // 맞는다. 0개는 "없다"가 명확하고, 부분은 "있다"로 위장한다.
        //
        // 편집본은 AI 산출물(565~567)이라 등록 시점에 없을 수 있다. 나온 뒤에
        // PUT /challenges/{seq}/participants (§3.7.5) 로 슬롯과 함께 얹는다.
        if (editedChallenges.isEmpty()) {
            return List.of();
        }

        List<Integer> masks = new ArrayList<>(editedChallenges.size());
        Set<Integer> unknown = new LinkedHashSet<>();
        Set<Integer> seen = new LinkedHashSet<>();
        Set<Integer> duplicate = new LinkedHashSet<>();
        for (EditedChallengeRequest edited : editedChallenges) {
            int mask = maskOf(edited.removedSlots(), slotCount);
            masks.add(mask);
            // 0(원본) · 범위 밖 인덱스는 기대 집합에 없는 값이므로 unknown 으로 묶는다
            if (mask <= 0 || mask > expected) {
                unknown.add(mask);
            } else if (!seen.add(mask)) {
                duplicate.add(mask);
            }
        }

        List<Integer> missing = new ArrayList<>();
        for (int mask = 1; mask <= expected; mask++) {
            if (!seen.contains(mask)) {
                missing.add(mask);
            }
        }
        if (!missing.isEmpty() || !duplicate.isEmpty() || !unknown.isEmpty()) {
            throw new BusinessException(AdminErrorCode.EDITED_SET_INCOMPLETE,
                    "편집본 조합 불완전: expected=%d missing=%s duplicate=%s unknown=%s"
                            .formatted(expected, missing, duplicate, unknown),
                    Map.of("expected", expected,
                            "missingMasks", missing,
                            "duplicateMasks", List.copyOf(duplicate),
                            "unknownMasks", List.copyOf(unknown)));
        }
        return masks;
    }

    /**
     * {@code removedSlots} → 비트 OR 합. 범위를 벗어난 인덱스·중복·빈 배열은
     * {@code -1}(= 기대 집합 밖)로 접어 호출부가 {@code unknownMasks} 로 보고하게 한다.
     */
    private static int maskOf(List<Integer> removedSlots, int slotCount) {
        if (removedSlots == null || removedSlots.isEmpty()) {
            return -1;
        }
        int mask = 0;
        for (Integer slot : removedSlots) {
            if (slot == null || slot < 0 || slot >= slotCount) {
                return -1;
            }
            int bit = participantMask(slot);
            if ((mask & bit) != 0) {
                return -1;      // 같은 슬롯을 두 번 적었다
            }
            mask |= bit;
        }
        return mask;
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

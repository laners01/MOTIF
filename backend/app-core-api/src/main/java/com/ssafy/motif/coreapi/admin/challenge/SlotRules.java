package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.AnchorRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;

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
 * 를 받는다. 어드민이 순서를 손으로 넣지 않으므로 인물과 자리가 어긋날 수 없다.
 *
 * <p><b>마스크 헬퍼는 여기 없다</b>(0020). 앱·어드민 API 가 슬롯을
 * {@code challengeParticipantSeq} 로 가리키게 되면서 마스크가 남은 곳은 AI 접수 페이로드
 * 하나뿐이고, 그 파생은
 * {@link com.ssafy.motif.domain.core.challenge.ChallengeParticipant#participantMask()} 가
 * 맡는다 — 파생 지점을 둘로 두면 한쪽만 고쳤을 때 어긋난다.
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
     *
     * <p><b>편집본에는 길이가 따라온다</b>({@code EDITED_DURATION_REQUIRED}, 0020) — §7.6 이
     * 앱에 내보내는 값이라 없으면 촬영 타이머를 그릴 수 없다.
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
            // 길이 없이 편집본만 받으면 §7.6 이 null 을 내보내고 앱 촬영 타이머가 멈춘다
            if (!blank(p.editedChallengeKey()) && p.durationMs() == null) {
                violations.add("EDITED_DURATION_REQUIRED[" + i + "]");
            }
            if (!protectedKeys(p)) {
                violations.add("EDITED_KEY_NOT_PROTECTED[" + i + "]");
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
     * 편집본 3키가 전부 보호 프리픽스인가 ({@code EDITED_KEY_NOT_PROTECTED}, 0020).
     *
     * <p><b>HEAD 만으로는 부족하다.</b> {@code requireUploaded} 는 "그 자리에 파일이 있나"
     * 만 보므로, AI 팀이 옛 경로({@code edited/…})에 올린 키를 그대로 등록하면 통과한다.
     * 그 상태로 저장되면 {@link MediaKeys#isProtected} 가 {@code false} 라 §7.5·§7.6 이
     * <b>서명 없이</b> 내보낸다 — 404 가 아니라 예외도 로그도 없이 보호가 풀린다.
     *
     * <p>어드민 presign 은 {@link com.ssafy.motif.coreapi.admin.media.AdminMediaKind} 가
     * 이미 {@code private/edited/…} 로 발급하므로 화면 업로드 경로는 저절로 통과한다.
     * 이 검사가 잡는 것은 <b>키를 손으로 붙여 넣는 경로</b>다.
     */
    private static boolean protectedKeys(ParticipantRequest p) {
        return isProtectedOrAbsent(p.editedChallengeKey())
                && isProtectedOrAbsent(p.initialSceneKey())
                && isProtectedOrAbsent(p.previewImageKey());
    }

    private static boolean isProtectedOrAbsent(String key) {
        return blank(key) || MediaKeys.isProtected(key);
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

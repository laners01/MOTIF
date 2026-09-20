package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.AnchorRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 챌린지 등록의 핵심 불변식 (S15P21D104-563 — 명세 §3.7.2 검증 2·3항).
 *
 * <p>{@link SlotRules} 가 순수 계산이라 DB·S3 없이 돌릴 수 있다 — <b>CI 에서 매번 돈다</b>
 * ({@code :app-core-api:test} 는 {@code local-db} 태그를 제외한다). all-or-nothing 롤백처럼
 * 실제 DB 가 필요한 케이스는 {@code AdminChallengeServiceLocalDbTest} 가 맡는다.
 */
class SlotRulesTest {

    @Test
    @DisplayName("슬롯 마스크는 배열 순서대로 1·2·4 — 어드민이 비트를 입력하지 않는다")
    void 슬롯_마스크() {
        assertThat(List.of(SlotRules.participantMask(0), SlotRules.participantMask(1),
                SlotRules.participantMask(2), SlotRules.participantMask(3)))
                .containsExactly(1, 2, 4, 8);
    }

    @Test
    @DisplayName("n=0(솔로) — 편집본 0개가 정답이다")
    void 솔로는_편집본이_없다() {
        assertThat(SlotRules.editedMasks(0, List.of())).isEmpty();
    }

    @Test
    @DisplayName("n=1 — 편집본 1개, 마스크 1")
    void 슬롯_하나() {
        assertThat(SlotRules.editedMasks(1, List.of(edited(0)))).containsExactly(1);
    }

    @Test
    @DisplayName("n=2 — removedSlots 가 마스크 1·2·3 으로 환산된다")
    void 슬롯_둘_전조합() {
        assertThat(SlotRules.editedMasks(2, List.of(edited(0), edited(1), edited(0, 1))))
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("n=2 에 편집본 2개 — 누락 마스크가 data 에 담긴다")
    void 누락() {
        assertThatThrownBy(() -> SlotRules.editedMasks(2, List.of(edited(0), edited(1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(AdminErrorCode.EDITED_SET_INCOMPLETE);
                    assertThat(data(be)).containsEntry("expected", 3)
                            .containsEntry("missingMasks", List.of(3));
                });
    }

    @Test
    @DisplayName("같은 조합을 두 번 — duplicateMasks 에 담기고, 빠진 조합도 함께 보고된다")
    void 중복() {
        assertThatThrownBy(() -> SlotRules.editedMasks(2, List.of(edited(0), edited(0), edited(1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(data((BusinessException) e))
                        .containsEntry("duplicateMasks", List.of(1))
                        .containsEntry("missingMasks", List.of(3)));
    }

    @Test
    @DisplayName("범위 밖 슬롯 인덱스는 unknownMasks — 기대 집합에 없는 값이다")
    void 범위_초과() {
        assertThatThrownBy(() -> SlotRules.editedMasks(2,
                List.of(edited(0), edited(1), edited(0, 1), edited(5))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(data((BusinessException) e))
                        .containsEntry("unknownMasks", List.of(-1)));
    }

    @Test
    @DisplayName("removedSlots 가 비면 마스크 0(원본) — 예약값이라 요청할 수 없다")
    void 빈_조합은_예약값() {
        assertThatThrownBy(() -> SlotRules.editedMasks(1, List.of(edited())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.EDITED_SET_INCOMPLETE);
    }

    @Test
    @DisplayName("슬롯끼리 아티스트가 겹치면 PARTICIPANT_INVALID — 마스크와 인물이 1:1 이어야 한다")
    void 슬롯_아티스트_규칙() {
        assertThatThrownBy(() -> SlotRules.checkParticipants(List.of(slot(7), slot(7))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.PARTICIPANT_INVALID);
    }

    @Test
    @DisplayName("메인 아티스트도 슬롯이 된다 — 2인 챌린지에서 둘 다 교체 대상일 수 있다(§7.2)")
    void 메인_아티스트도_슬롯() {
        // 메인(3)이 슬롯에 그대로 들어와도 통과한다. 메인은 챌린지 귀속이고
        // 슬롯은 영상 속 자리라 층이 다르다 — 빼면 그 자리를 아무도 못 고른다
        assertThatCode(() -> SlotRules.checkParticipants(List.of(slot(3), slot(7))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("앵커는 0≤x,y≤1 · 0<r≤0.5 비율 — 위반을 한 번에 모아 던진다")
    void 앵커_규칙() {
        ParticipantRequest 밖 = new ParticipantRequest(7, new AnchorRequest(1.2, 0.5, 0.1));
        ParticipantRequest 반지름0 = new ParticipantRequest(9, new AnchorRequest(0.5, 0.5, 0.0));
        ParticipantRequest 경계 = new ParticipantRequest(11, new AnchorRequest(0.0, 1.0, 0.5));

        assertThatThrownBy(() -> SlotRules.checkParticipants(List.of(밖, 반지름0, 경계)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .containsExactly("ANCHOR_INVALID[0]", "ANCHOR_INVALID[1]"));
    }

    @Test
    @DisplayName("슬롯 하드 상한은 4 — 편집본 15개가 한계다")
    void 슬롯_상한() {
        assertThatThrownBy(() -> SlotRules.checkParticipants(
                List.of(slot(11), slot(12), slot(13), slot(14), slot(15))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .contains("SLOT_COUNT_EXCEEDED"));
    }

    private static ParticipantRequest slot(int artistSeq) {
        return new ParticipantRequest(artistSeq, new AnchorRequest(0.5, 0.4, 0.0995));
    }

    private static EditedChallengeRequest edited(Integer... removedSlots) {
        return new EditedChallengeRequest(List.of(removedSlots),
                "edited/1/v.mp4", "edited/1/s.jpg", "edited/1/p.jpg", null);
    }

    @SuppressWarnings("unchecked")
    private static List<String> reasons(BusinessException e) {
        return (List<String>) e.getData();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(BusinessException e) {
        return (Map<String, Object>) e.getData();
    }

    /**
     * 편집본 0개 허용 (2026-09-14 확정).
     *
     * <p>AI 산출물(565~567)이 나오기 전에도 "같이 춘 사람"을 슬롯에 넣어 등록할 수 있어야
     * 한다 — 설명 시트의 참여자 아바타가 슬롯을 그대로 쓴다. 영상 재생에는 편집본이
     * 쓰이지 않고, 촬영 시도는 §13.1 의 EDITED_CHALLENGE_NOT_FOUND 가 막는다.
     */
    @Test
    @DisplayName("슬롯이 있어도 편집본 0개면 통과한다 — 표시 전용 챌린지")
    void emptyEditedIsAllowedEvenWithSlots() {
        assertThat(SlotRules.editedMasks(3, List.of())).isEmpty();
    }

    /**
     * 0개를 허용해도 <b>부분 집합은 계속 막는다</b>. 이게 이 완화의 경계다 —
     * 0개는 "없다"가 명확하고, 부분은 목록에서 "있다"로 보이면서 특정 조합을 고른
     * 사용자만 촬영 직전에 404 를 맞는다.
     */
    @Test
    @DisplayName("부분 집합은 여전히 막는다 — 3개 중 2개")
    void partialEditedStillRejected() {
        assertThatThrownBy(() -> SlotRules.editedMasks(2, List.of(edited(0), edited(1))))
                .isInstanceOf(BusinessException.class);
    }
}

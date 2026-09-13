package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.BboxRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.EditedChallengeRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("메인 아티스트를 슬롯에 넣거나 슬롯끼리 겹치면 PARTICIPANT_INVALID")
    void 슬롯_아티스트_규칙() {
        assertThatThrownBy(() -> SlotRules.checkParticipants(3, List.of(slot(3))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.PARTICIPANT_INVALID);

        assertThatThrownBy(() -> SlotRules.checkParticipants(3, List.of(slot(7), slot(7))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.PARTICIPANT_INVALID);
    }

    @Test
    @DisplayName("bbox 는 우하단 > 좌상단 ≥ 0 — 위반을 한 번에 모아 던진다")
    void bbox_규칙() {
        ParticipantRequest 음수 = new ParticipantRequest(7, new BboxRequest(-1, 0, 10, 10));
        ParticipantRequest 뒤집힘 = new ParticipantRequest(9, new BboxRequest(10, 10, 10, 20));

        assertThatThrownBy(() -> SlotRules.checkParticipants(3, List.of(음수, 뒤집힘)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .containsExactly("BBOX_INVALID[0]", "BBOX_INVALID[1]"));
    }

    @Test
    @DisplayName("슬롯 하드 상한은 4 — 편집본 15개가 한계다")
    void 슬롯_상한() {
        assertThatThrownBy(() -> SlotRules.checkParticipants(3,
                List.of(slot(11), slot(12), slot(13), slot(14), slot(15))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .contains("SLOT_COUNT_EXCEEDED"));
    }

    private static ParticipantRequest slot(int artistSeq) {
        return new ParticipantRequest(artistSeq, new BboxRequest(0, 0, 100, 200));
    }

    private static EditedChallengeRequest edited(Integer... removedSlots) {
        return new EditedChallengeRequest(List.of(removedSlots),
                "edited/1/v.mp4", "edited/1/s.jpg", null);
    }

    @SuppressWarnings("unchecked")
    private static List<String> reasons(BusinessException e) {
        return (List<String>) e.getData();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(BusinessException e) {
        return (Map<String, Object>) e.getData();
    }
}

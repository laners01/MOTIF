package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.challenge.dto.AnchorRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ParticipantRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 챌린지 등록의 슬롯 불변식 (S15P21D104-563 — 명세 §3.7.2 검증 2항).
 *
 * <p>{@link SlotRules} 가 순수 계산이라 DB·S3 없이 돌릴 수 있다 — <b>CI 에서 매번 돈다</b>
 * ({@code :app-core-api:test} 는 {@code local-db} 태그를 제외한다). all-or-nothing 롤백처럼
 * 실제 DB 가 필요한 케이스는 {@code AdminChallengeServiceLocalDbTest} 가 맡는다.
 *
 * <p>0019 로 <b>조합 완결성 검사가 통째로 없어졌다</b> — 편집본이 슬롯 1개당 1개라 마스크
 * 조합이라는 개념 자체가 사라졌다. 0020 에서는 <b>마스크 헬퍼까지 없어졌다</b> — 앱·어드민이
 * 슬롯을 {@code challengeParticipantSeq} 로 가리키게 되면서 마스크가 남은 곳은 AI 접수
 * 페이로드 하나뿐이고, 그 파생은 엔티티가 맡는다. 남은 검사는 인물·앵커·편집본 짝이다.
 */
class SlotRulesTest {

    @Test
    @DisplayName("슬롯끼리 아티스트가 겹치면 PARTICIPANT_INVALID — 자리와 인물이 1:1 이어야 한다")
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
        ParticipantRequest 밖 = participant(7, new AnchorRequest(1.2, 0.5, 0.1));
        ParticipantRequest 반지름0 = participant(9, new AnchorRequest(0.5, 0.5, 0.0));
        ParticipantRequest 경계 = participant(11, new AnchorRequest(0.0, 1.0, 0.5));

        assertThatThrownBy(() -> SlotRules.checkParticipants(List.of(밖, 반지름0, 경계)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .containsExactly("ANCHOR_INVALID[0]", "ANCHOR_INVALID[1]"));
    }

    @Test
    @DisplayName("슬롯 하드 상한은 4")
    void 슬롯_상한() {
        assertThatThrownBy(() -> SlotRules.checkParticipants(
                List.of(slot(11), slot(12), slot(13), slot(14), slot(15))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .contains("SLOT_COUNT_EXCEEDED"));
    }

    /**
     * 편집본 0개 허용 (2026-09-14 확정, 0019 에서도 유지).
     *
     * <p>AI 산출물(565~567)이 나오기 전에도 "같이 춘 사람"을 슬롯에 넣어 등록할 수 있어야
     * 한다 — 설명 시트의 참여자 아바타가 슬롯을 그대로 쓴다. 영상 재생에는 편집본이
     * 쓰이지 않고, 촬영 시도는 §13.1 의 EDITED_CHALLENGE_NOT_FOUND 가 막는다.
     */
    @Test
    @DisplayName("편집본 없는 슬롯은 통과한다 — 표시 전용 챌린지")
    void 편집본_없는_슬롯() {
        assertThatCode(() -> SlotRules.checkParticipants(List.of(slot(3), slot(7), slot(9))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일부 슬롯만 편집본이 있어도 된다 — 나머지는 앱에서 고를 수 없을 뿐이다")
    void 일부만_편집본() {
        assertThatCode(() -> SlotRules.checkParticipants(List.of(slot(3), edited(7))))
                .doesNotThrowAnyException();
    }

    /**
     * {@code ck_challenge_participants_edited} 와 같은 규칙이다 — 한쪽만 채운 행을 DB 가
     * 거부하므로 여기서 먼저 400 으로 돌려준다. 통과시키면 500 으로 샌다.
     */
    @Test
    @DisplayName("영상만 있고 첫 장면이 없으면 EDITED_KEYS_INCOMPLETE — 둘은 짝이다")
    void 편집본_키는_짝() {
        // 길이는 채워 둔다 — 안 그러면 EDITED_DURATION_REQUIRED 까지 섞여 무엇을 보는지 흐려진다
        ParticipantRequest 영상만 = new ParticipantRequest(7, anchor(),
                "private/edited/1/v.mp4", null, null, 15_000L);
        ParticipantRequest 장면만 = new ParticipantRequest(9, anchor(),
                null, "private/edited/1/s.jpg", null, null);

        assertThatThrownBy(() -> SlotRules.checkParticipants(List.of(영상만, 장면만)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .containsExactly("EDITED_KEYS_INCOMPLETE[0]", "EDITED_KEYS_INCOMPLETE[1]"));
    }

    @Test
    @DisplayName("편집본 없이 선택 화면만 올리면 막는다 — 고를 수 없는 그림이 남는다")
    void 편집본_없는_선택화면() {
        ParticipantRequest 프리뷰만 = new ParticipantRequest(7, anchor(),
                null, null, "private/edited/1/p.jpg", null);

        assertThatThrownBy(() -> SlotRules.checkParticipants(List.of(프리뷰만)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .containsExactly("PREVIEW_WITHOUT_EDITED[0]"));
    }

    /**
     * 0020 — §7.6 이 앱에 길이를 내보내므로 편집본에는 길이가 따라와야 한다. 없으면
     * 촬영 타이머를 그릴 수 없고, 챌린지 원본 길이로 갈음하면 ±500ms 만큼 끝이 잘린다.
     */
    @Test
    @DisplayName("편집본에 길이가 없으면 EDITED_DURATION_REQUIRED")
    void 편집본에는_길이가_따라온다() {
        ParticipantRequest 길이없음 = new ParticipantRequest(7, anchor(),
                "private/edited/1/v.mp4", "private/edited/1/s.jpg", null, null);

        assertThatThrownBy(() -> SlotRules.checkParticipants(List.of(길이없음)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(reasons((BusinessException) e))
                        .containsExactly("EDITED_DURATION_REQUIRED[0]"));
    }

    private static AnchorRequest anchor() {
        return new AnchorRequest(0.5, 0.4, 0.0995);
    }

    private static ParticipantRequest participant(int artistSeq, AnchorRequest anchor) {
        return new ParticipantRequest(artistSeq, anchor, null, null, null, null);
    }

    /** 편집본 없는 슬롯 — 0019 이후 정상 상태다. */
    private static ParticipantRequest slot(int artistSeq) {
        return participant(artistSeq, anchor());
    }

    /** 편집본이 붙은 슬롯 — 길이까지 있어야 통과한다(0020). */
    private static ParticipantRequest edited(int artistSeq) {
        return new ParticipantRequest(artistSeq, anchor(),
                "private/edited/1/v.mp4", "private/edited/1/s.jpg", "private/edited/1/p.jpg",
                15_000L);
    }

    @SuppressWarnings("unchecked")
    private static List<String> reasons(BusinessException e) {
        return (List<String>) e.getData();
    }
}

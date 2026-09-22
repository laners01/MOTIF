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
 * 페이로드 하나뿐이고, 그 파생은 엔티티가 맡는다.
 *
 * <p>2026-09-22 로 <b>편집본 검사도 여기서 빠졌다</b> — 편집본 키가 슬롯 seq 를 품게 되면서
 * 등록 요청에 실을 수 없게 됐고(§3.7.6 이 유일한 입구), 키 짝·길이·프리픽스 검사가 전부
 * 그쪽으로 갔다. 그 케이스는 {@code AdminChallengeServiceLocalDbTest} 가 본다 — 슬롯 행을
 * 읽어 경로를 대조하므로 DB 가 필요하다. 여기 남은 것은 인물·앵커·개수다.
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
     * <p>2026-09-22 이후로는 <b>등록 직후 모든 슬롯이 이 상태</b>다 — 편집본은 슬롯이 난
     * 뒤 §3.7.6 으로 채운다. 영상 재생에는 편집본이 쓰이지 않고, 촬영 시도는 §13.1 의
     * {@code EDITED_CHALLENGE_NOT_FOUND} 가 막는다.
     */
    @Test
    @DisplayName("편집본 없는 슬롯은 통과한다 — 등록 직후의 정상 상태다")
    void 편집본_없는_슬롯() {
        assertThatCode(() -> SlotRules.checkParticipants(List.of(slot(3), slot(7), slot(9))))
                .doesNotThrowAnyException();
    }

    private static AnchorRequest anchor() {
        return new AnchorRequest(0.5, 0.4, 0.0995);
    }

    private static ParticipantRequest participant(int artistSeq, AnchorRequest anchor) {
        return new ParticipantRequest(artistSeq, anchor);
    }

    private static ParticipantRequest slot(int artistSeq) {
        return participant(artistSeq, anchor());
    }

    @SuppressWarnings("unchecked")
    private static List<String> reasons(BusinessException e) {
        return (List<String>) e.getData();
    }
}

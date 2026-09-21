package com.ssafy.motif.domain.core.challenge;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 교체 가능한 인물 슬롯 + 그 사람을 지운 편집본 (DB SPEC v0.15 §13, changeSet 0004-02 · 0017-01 · 0019).
 *
 * <p><b>편집본은 슬롯 1개당 1개다</b>(0019). 예전에는 {@code edited_challenges} 가 "지운 슬롯
 * 조합" 단위라 3인 챌린지면 {@code {0}·{1}·{0,1}·{0,1,2}} … 가 각각 한 행이었지만, 사용자가
 * 고르는 것은 <b>함께할 아티스트 한 명</b>이므로 2명 이상을 한 번에 지운 편집본은 만들지
 * 않는다. 그래서 편집본이 슬롯 행으로 들어왔고 조합을 표현하던 마스크 칼럼은 사라졌다.
 *
 * <p><b>{@code slotOrder} 는 화면 좌→우 순서(0부터)다.</b> 서버가 요청 배열 순서대로 부여한다
 * (어드민 명세 §3.7.2) — 어드민이 직접 입력하지 않는다.
 *
 * <p><b>재정렬용 숫자가 아니다.</b> {@code notice_images.display_order} 처럼 자유롭게 옮길 수
 * 있는 표시 순서로 읽으면 안 된다 — 이 값은 {@link #anchorOrNull() 앵커}·편집본과 짝이고 AI
 * 접수 페이로드의 {@code participantMask} 가 여기서 파생되므로({@link #participantMask()}),
 * 순서를 바꾸면 <b>합성이 교체하는 인물이 바뀐다</b>. 슬롯 구성은 개별 이동이 아니라 통째로
 * 지웠다 다시 만든다(§3.7.5).
 */
@Entity
@Table(name = "challenge_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeParticipant extends BaseTimeEntity {

    /**
     * 대리키 (0019) — 복합키를 대신한다.
     *
     * <p>{@code composite_jobs.challenge_participant_seq} 가 이 값을 가리킨다. 복합키였다면
     * 잡 행이 칼럼 두 개로 편집본을 참조해야 했다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_participant_seq")
    private Integer challengeParticipantSeq;

    @Column(name = "challenge_seq", nullable = false, updatable = false)
    private Integer challengeSeq;

    @Column(name = "artist_seq", nullable = false, updatable = false)
    private Integer artistSeq;

    /** 화면 좌→우, 0부터. {@code uq_challenge_participants_slot_order} 가 챌린지 안의 유일성을 잠근다. */
    @Column(name = "slot_order", nullable = false, updatable = false)
    private int slotOrder;

    /**
     * 촬영 전 선택 화면에서 이 슬롯의 프로필 원이 앉는 자리 — 썸네일 대비 0~1 비율.
     * <b>{@code null} 은 0017 이전 행</b>이다. 신규 등록은 서비스가 필수로 받고,
     * 옛 행은 어드민 상세에서 {@link #updateAnchor} 로 채운다.
     */
    @Embedded
    private Anchor anchor;

    /**
     * 이 사람을 지운 편집본 영상 키 — 합성의 베이스가 된다.
     *
     * <p>{@code null} 이면 이 슬롯에는 편집본이 없다. 사용자가 그 슬롯을 고르면 §13.1 이
     * {@code EDITED_CHALLENGE_NOT_FOUND} 로 막는다 — 전조합 완결성 강제는 없앴으므로
     * (어드민이 만들지 않은 슬롯은 그냥 비어 있다) 정상적으로 일어날 수 있는 상태다.
     */
    @Column(name = "edited_challenge_key", length = 255)
    private String editedChallengeKey;

    /** 편집본의 첫 장면. {@code ck_challenge_participants_edited} 가 영상 키와 짝을 강제한다. */
    @Column(name = "initial_scene_key", length = 255)
    private String initialSceneKey;

    /**
     * 편집본 길이(ms) — 앱 촬영 타이머가 이 값에 맞춰 돈다 (0020).
     *
     * <p><b>챌린지 원본 길이로 갈음하지 않는다.</b> 등록 검증이 원본 ±500ms 만 보장하므로
     * 최대 0.5초가 어긋나는데, 그만큼 녹화 끝이 잘린다.
     *
     * <p>통짜 MP4 라 필요한 값이기도 하다 — HLS 면 재생목록의 {@code #EXTINF} 합으로
     * 플레이어가 알아내지만, MP4 는 {@code moov} 를 읽기 전까지 길이를 모른다. 재생 전에
     * 타이머 UI 를 그리려면 서버가 줘야 한다.
     */
    @Column(name = "edited_duration_ms")
    private Integer editedDurationMs;

    /**
     * 촬영 전 선택 화면(시안 10-2·10-3)에서 이 슬롯을 골랐을 때 썸네일 자리에 보이는
     * 이미지(AI팀 산출, changeSet 0017-02).
     *
     * <p>편집본이 있어도 <b>{@code null} 일 수 있다</b> — 0017 이전 행이거나 아직 안 올린
     * 경우다. 그래서 CHECK 의 짝 강제에 들어가지 않는다. 클라이언트는 챌린지 썸네일로 폴백한다.
     */
    @Column(name = "preview_image_key", length = 255)
    private String previewImageKey;

    /**
     * 프로필 원 중심 {@code (x, y)} 와 반지름 {@code r}, 전부 비율이다 — {@code x·r} 은 썸네일
     * 가로, {@code y} 는 세로 대비. 시안(402×874, 지름 80)의 {@code r = 40/402 ≈ 0.0995} 가
     * 어드민 기본값이다. 클라이언트는 렌더 크기를 곱해 px 로 만든다. DB CHECK 가
     * {@code 0 ≤ x,y ≤ 1 · 0 < r ≤ 0.5} 를 강제한다.
     */
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Anchor {

        @Column(name = "anchor_x")
        private Double x;

        @Column(name = "anchor_y")
        private Double y;

        @Column(name = "anchor_r")
        private Double r;

        public Anchor(double x, double y, double r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }

        /** 세 컬럼이 전부 NULL 인 옛 행은 Hibernate 가 임베디드를 {@code null} 로 주지만, 부분 NULL 도 같은 뜻으로 본다. */
        public boolean isSet() {
            return x != null && y != null && r != null;
        }
    }

    private ChallengeParticipant(Integer challengeSeq, Integer artistSeq, int slotOrder,
                                 Anchor anchor, String editedChallengeKey,
                                 String initialSceneKey, String previewImageKey,
                                 Integer editedDurationMs) {
        this.challengeSeq = challengeSeq;
        this.artistSeq = artistSeq;
        this.slotOrder = slotOrder;
        this.anchor = anchor;
        this.editedChallengeKey = editedChallengeKey;
        this.initialSceneKey = initialSceneKey;
        this.previewImageKey = previewImageKey;
        this.editedDurationMs = editedDurationMs;
    }

    /**
     * @param editedChallengeKey 편집본이 없으면 {@code null} — {@code initialSceneKey} 도 함께
     *                           {@code null} 이어야 한다(DB CHECK)
     * @param editedDurationMs   편집본 길이. 편집본이 있으면 서비스가 필수로 받는다(§3.7.2)
     */
    public static ChallengeParticipant of(Integer challengeSeq, Integer artistSeq, int slotOrder,
                                          Anchor anchor, String editedChallengeKey,
                                          String initialSceneKey, String previewImageKey,
                                          Integer editedDurationMs) {
        return new ChallengeParticipant(challengeSeq, artistSeq, slotOrder, anchor,
                editedChallengeKey, initialSceneKey, previewImageKey, editedDurationMs);
    }

    /** 앵커만 고친다(§3.7.9) — 슬롯 구성·순서는 그대로라 편집본·합성에 영향이 없다. */
    public void updateAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    /**
     * 편집본 키 교체 (§3.7.6) — 부분 수정이라 {@code null} 은 "그대로" 다. 슬롯은 바뀌지 않는다.
     *
     * <p>합성 잡이 참조 중이어도 허용된다 — 결과물은 별도 오브젝트라 이미 만들어진 결과가
     * 바뀌지 않고, 다음 합성부터 새 키를 쓴다.
     */
    public void replaceEditedKeys(String editedChallengeKey, String initialSceneKey,
                                  String previewImageKey, Integer editedDurationMs) {
        if (editedChallengeKey != null) {
            this.editedChallengeKey = editedChallengeKey;
        }
        if (initialSceneKey != null) {
            this.initialSceneKey = initialSceneKey;
        }
        if (previewImageKey != null) {
            this.previewImageKey = previewImageKey;
        }
        // 영상이 바뀌면 길이도 따라 바뀐다 — 옛 길이가 남으면 촬영 타이머가 어긋난다
        if (editedDurationMs != null) {
            this.editedDurationMs = editedDurationMs;
        }
    }

    /**
     * AI 접수 페이로드가 쓰는 <b>단일 비트</b> 마스크 (AI 서버 명세 §3.2) — {@code 1 << slotOrder}.
     *
     * <p>저장하지 않고 파생한다. 칼럼으로 두면 순서와 마스크가 어긋날 수 있는데, 둘은 같은
     * 사실의 두 표현이라 어긋날 자리를 아예 없앴다.
     */
    public int participantMask() {
        return 1 << slotOrder;
    }

    /** 이 슬롯을 골랐을 때 합성 베이스가 있는가. 없으면 §13.1 이 404 로 막는다. */
    public boolean hasEdited() {
        return editedChallengeKey != null;
    }

    /** 앵커가 채워진 행만 값을 준다 — 옛 행은 {@code null}. */
    public Anchor anchorOrNull() {
        return anchor != null && anchor.isSet() ? anchor : null;
    }
}

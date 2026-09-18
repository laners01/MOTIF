package com.ssafy.motif.domain.core.challenge;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 슬롯 조합별 사전 편집본 (DB SPEC v0.15 §14, changeSet 0004-03).
 *
 * <p>여기의 {@code participantMask} 는 <b>비트 OR 합</b>이라 단일 비트가 아니다 —
 * 슬롯 0·1 을 함께 뺀 편집본은 {@code 3} 이다. 슬롯 n 개면 {@code 1 … 2ⁿ−1} 전조합이
 * 빠짐없이 있어야 한다(어드민 명세 §3.7.2). 전제가 깨지면 사용자가 촬영 직전에
 * {@code EDITED_CHALLENGE_NOT_FOUND} 를 만난다(API 명세 §13.1).
 *
 * <p>키 교체는 <b>같은 행 UPDATE</b> 다(§3.7.6) — 합성 잡이 참조 중이어도 허용된다.
 * 결과물은 별도 오브젝트라 이미 만들어진 결과가 바뀌지 않고, 다음 합성부터 새 키를 쓴다.
 *
 * <p>{@code previewImageKey} 는 촬영 전 선택 화면(시안 10-2·10-3)에서 이 조합을 골랐을 때
 * 썸네일 자리에 보이는 이미지다(changeSet 0017-02). mask 당 1장이라 이 행에 붙인다 —
 * mask 0(무선택)은 행이 없고 챌린지 썸네일이 그 화면이다. <b>{@code null} 은 0017 이전
 * 행</b>이며 신규 등록은 서비스가 필수로 받는다.
 */
@Entity
@Table(name = "edited_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EditedChallenge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "edited_challenge_seq")
    private Integer editedChallengeSeq;

    @Column(name = "challenge_seq", nullable = false, updatable = false)
    private Integer challengeSeq;

    @Column(name = "participant_mask", nullable = false, updatable = false)
    private int participantMask;

    @Column(name = "video_key", nullable = false)
    private String videoKey;

    @Column(name = "initial_scene_key", nullable = false)
    private String initialSceneKey;

    @Column(name = "preview_image_key")
    private String previewImageKey;

    private EditedChallenge(Integer challengeSeq, int participantMask,
                            String videoKey, String initialSceneKey, String previewImageKey) {
        this.challengeSeq = challengeSeq;
        this.participantMask = participantMask;
        this.videoKey = videoKey;
        this.initialSceneKey = initialSceneKey;
        this.previewImageKey = previewImageKey;
    }

    public static EditedChallenge of(Integer challengeSeq, int participantMask,
                                     String videoKey, String initialSceneKey, String previewImageKey) {
        return new EditedChallenge(challengeSeq, participantMask, videoKey, initialSceneKey, previewImageKey);
    }

    /** 키 교체 (§3.7.6) — 부분 수정이라 {@code null} 은 "그대로" 다. 조합은 바뀌지 않는다. */
    public void replaceKeys(String videoKey, String initialSceneKey, String previewImageKey) {
        if (videoKey != null) {
            this.videoKey = videoKey;
        }
        if (initialSceneKey != null) {
            this.initialSceneKey = initialSceneKey;
        }
        if (previewImageKey != null) {
            this.previewImageKey = previewImageKey;
        }
    }
}

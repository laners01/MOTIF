package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.coreapi.global.media.MediaUploadProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 어드민 미디어 설정 — 영상 presign 조건(§1.4)과 영상 검증 규칙(§1.4 2항)
 * (S15P21D104-588·589).
 *
 * <p><b>이미지 한도는 여기 없다</b> — 어드민 아티스트 프로필과 사용자 프로필(API §4.3)이
 * 같은 {@code profiles/} 프리픽스에 쓰므로 {@link MediaUploadProperties} 가 소유한다.
 * 영상은 어드민만 올리므로 여기 남는다.
 *
 * <p><b>수치를 코드에 박지 않는다.</b> 검증 기준은 §8 #3 미결이라 회의에서 바뀔 수 있고,
 * configmap 만 고쳐 재배포 없이 반영하는 것이 요건이다(589 DoD).
 *
 * @param video 영상 presign 조건 — {@code contentTypes}·{@code maxSize} 는 presigned PUT
 *              서명에 박혀 강제된다. 서명된 헤더와 다른 값으로 PUT 하면 S3 가 거부한다
 * @param rules 등록 API 가 폼 값({@code durationMs}·{@code width}·{@code height})을
 *              대조하는 규칙 — {@link VideoRules}
 */
@ConfigurationProperties(prefix = "motif.admin.media")
public record AdminMediaProperties(MediaUploadProperties.Upload video, Rules rules) {

    /**
     * @param challengeMaxDuration 챌린지 원본 최대 길이 (명세 §1.4 — 60초)
     * @param assetMaxDuration     모티프 에셋 최대 길이 (3분)
     * @param editedTolerance      편집본과 원본의 허용 길이 차 (±500ms)
     */
    public record Rules(Duration challengeMaxDuration, Duration assetMaxDuration,
                        Duration editedTolerance) {
    }
}

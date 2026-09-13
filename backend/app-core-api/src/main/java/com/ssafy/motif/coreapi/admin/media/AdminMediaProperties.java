package com.ssafy.motif.coreapi.admin.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * 어드민 미디어 설정 — presign 조건(§1.4)과 영상 검증 규칙(§1.4 2항) (S15P21D104-588·589).
 *
 * <p><b>수치를 코드에 박지 않는다.</b> 검증 기준은 §8 #3 미결이라 회의에서 바뀔 수 있고,
 * configmap 만 고쳐 재배포 없이 반영하는 것이 요건이다(589 DoD).
 *
 * @param video 영상 presign 조건 — {@code contentTypes}·{@code maxSize} 는 presigned PUT
 *              서명에 박혀 강제된다. 서명된 헤더와 다른 값으로 PUT 하면 S3 가 거부한다
 * @param image 이미지 presign 조건
 * @param rules 등록 API 가 폼 값({@code durationMs}·{@code width}·{@code height})을
 *              대조하는 규칙 — {@link VideoRules}
 */
@ConfigurationProperties(prefix = "motif.admin.media")
public record AdminMediaProperties(Upload video, Upload image, Rules rules) {

    /**
     * @param presignTtl presigned PUT 만료 — 명세 §1.4 는 영상 600초·이미지 300초
     */
    public record Upload(List<String> contentTypes, DataSize maxSize, Duration presignTtl) {
    }

    /**
     * @param challengeMaxDuration 챌린지 원본 최대 길이 (명세 §1.4 — 60초)
     * @param assetMaxDuration     모티프 에셋 최대 길이 (20초)
     * @param editedTolerance      편집본과 원본의 허용 길이 차 (±500ms)
     */
    public record Rules(Duration challengeMaxDuration, Duration assetMaxDuration,
                        Duration editedTolerance) {
    }
}

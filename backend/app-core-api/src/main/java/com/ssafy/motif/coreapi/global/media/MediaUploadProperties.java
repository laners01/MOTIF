package com.ssafy.motif.coreapi.global.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * 업로드 presign 조건 중 <b>경로를 공유하는 것</b> (어드민 명세 §1.4 · API 명세 §4.3).
 *
 * <p><b>이미지 한도가 여기 있는 이유.</b> 어드민이 올리는 아티스트 프로필
 * ({@code AdminMediaKind.ARTIST_PROFILE})과 사용자가 올리는 자기 프로필(§4.3)이
 * <b>같은 {@code profiles/} 프리픽스에 쓴다.</b> 한도를 각자 두면 한쪽만 webp 를
 * 허용하는 식으로 갈리는데, 그러면 같은 자리에 규칙이 둘이 된다.
 *
 * <p>영상 한도와 길이·해상도 규칙은 여기 없다 — 어드민만 영상을 올리므로
 * {@code AdminMediaProperties} 가 계속 소유한다.
 *
 * <p><b>수치를 코드에 박지 않는다.</b> configmap 만 고쳐 재배포 없이 반영하는 것이
 * 요건이다(S15P21D104-589 DoD).
 *
 * @param image 이미지 presign 조건 — {@code contentTypes}·{@code maxSize} 는
 *              presigned PUT 서명에 박혀 강제된다. 서명된 헤더와 다른 값으로 PUT 하면
 *              S3 가 거부한다
 */
@ConfigurationProperties(prefix = "motif.media.upload")
public record MediaUploadProperties(Upload image) {

    /**
     * @param presignTtl presigned PUT 만료 — 어드민 명세 §1.4 는 영상 600초·이미지 300초,
     *                   API 명세 §4.3 의 {@code expiresIn} 도 300 이다
     */
    public record Upload(List<String> contentTypes, DataSize maxSize, Duration presignTtl) {
    }
}

package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.user.dto.ProfileImagePresignResponse;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.coreapi.global.media.MediaUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 프로필 이미지 업로드 URL 발급 (API 명세 §4.3) — S15P21D104-280.
 *
 * <p>바이트는 앱 → S3 로 직행하고 서버는 키만 받는다(설계 원칙 ① · Valet Key).
 * 반영은 §4.4({@link UserCommandService#changeProfileImage})가 한다 — 발급과 반영을
 * 나눈 이유는 그 사이에 클라이언트의 PUT 이 끼기 때문이다.
 *
 * <p><b>발급은 "올려도 된다"는 허가일 뿐 "올렸다"는 증거가 아니다.</b> 그래서 §4.4 가
 * HEAD 로 실재를 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileImageService {

    /**
     * 프로필 이미지 키 — {@code profiles/{uuid}.jpg}.
     *
     * <p><b>{@code AdminMediaKind.ARTIST_PROFILE} 과 같은 모양이어야 한다.</b> 어드민이
     * 올리는 아티스트 프로필과 <b>같은 프리픽스를 공유한다</b>(그쪽 javadoc 이 그렇게
     * 선언해 뒀다) — 갈리면 한 프리픽스 안에 규약이 둘이 된다. 열거를 직접 쓰지 않는
     * 것은 팬 도메인이 어드민 패키지에 의존하지 않기 위해서다.
     *
     * <p>확장자는 {@code contentType} 과 무관하게 고정이다. 실제 미디어 타입은 PUT 시
     * 서명된 {@code Content-Type} 으로 S3 에 기록되므로 서빙에 문제가 없다.
     */
    private static final String KEY_PREFIX = "profiles/";
    private static final String KEY_EXTENSION = ".jpg";

    private final MediaObjectStore objectStore;
    private final MediaUploadProperties uploadProperties;
    private final ProfileImagePresignRegistry presignRegistry;

    /** §4.3 업로드 URL 발급. */
    public ProfileImagePresignResponse presign(Integer userSeq, String contentType, long contentLength) {
        MediaUploadProperties.Upload limits = uploadProperties.image();
        verify(contentType, contentLength, limits);

        String imageKey = KEY_PREFIX + UUID.randomUUID() + KEY_EXTENSION;
        String uploadUrl = objectStore.presignPut(imageKey, contentType, contentLength, limits.presignTtl());

        // 발급 기록의 수명은 URL 의 수명과 같다 — 만료되면 그 키를 주장할 근거도 사라진다
        presignRegistry.remember(userSeq, imageKey, limits.presignTtl());

        log.info("프로필 이미지 presign 발급: userSeq={} key={} size={}", userSeq, imageKey, contentLength);
        return new ProfileImagePresignResponse(uploadUrl, imageKey, limits.presignTtl().toSeconds());
    }

    /**
     * 허용 조건 확인 — <b>발급 전에</b> 본다.
     *
     * <p>어드민(§3.2)은 형식·크기를 {@code MEDIA_TYPE_NOT_ALLOWED} 로 묶지만 여기는
     * 팬 API 라 §1.5 공통 {@code INVALID_PARAMETER} 다. 어드민 에러 코드를 팬 앱 응답으로
     * 내보내지 않는다.
     *
     * <p>이 검사를 통과한 값이 <b>서명에 박혀</b> S3 에서 한 번 더 강제된다 — 여기서만
     * 막으면 발급된 URL 로 다른 값을 올릴 수 있다.
     */
    private static void verify(String contentType, long contentLength, MediaUploadProperties.Upload limits) {
        if (!limits.contentTypes().contains(contentType)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "허용되지 않는 Content-Type: " + contentType,
                    Map.of("allowedContentTypes", limits.contentTypes()));
        }
        long maxBytes = limits.maxSize().toBytes();
        if (contentLength > maxBytes) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "크기 상한 초과: contentLength=%d max=%d".formatted(contentLength, maxBytes),
                    Map.of("maxBytes", maxBytes));
        }
    }
}

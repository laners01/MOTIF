package com.ssafy.motif.coreapi.global.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * S3 키 → <b>공개 등급</b> CloudFront URL 변환 (명세 §1.7).
 *
 * <p>대상은 캐시를 최대화하는 공개 콘텐츠다 — 프로필·썸네일·챌린지 원본·PUBLIC 참여
 * 영상·에셋 미리보기·공지 이미지·숍 배너. <b>보호 등급(모티프 결과물, 본인 PRIVATE
 * 영상, 게시 전 프리뷰)은 여기서 만들지 않는다</b> — signed URL 발급은 별도 관심사다.
 *
 * <p>base URL 이 비어 있으면 항상 {@code null} 을 준다. 로컬·CI 에서 CDN 없이 기동해도
 * 인증·조회 기능 개발이 막히지 않게 하려는 선택이며, 값 확정은 §4.3 presign 티켓이다.
 */
@Component
public class PublicMediaUrlResolver {

    private final String baseUrl;

    public PublicMediaUrlResolver(@Value("${motif.media.public-base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** 키가 없거나 base URL 미설정이면 {@code null}. */
    public String resolve(String key) {
        if (key == null || key.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl + key : baseUrl + "/" + key;
    }
}

package com.ssafy.motif.coreapi.global.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * S3 키 → <b>보호 등급</b> CloudFront signed URL (API 명세 §1.7).
 *
 * <p>{@link PublicMediaUrlResolver} 와 짝이다. 대상은 {@code private/} 아래 — 모티프
 * 결과물, 본인 PRIVATE 참여 영상, 게시 전 합성 결과 프리뷰. 최상위 프리픽스가 곧 보호
 * 등급이라({@code MediaKeys}) CloudFront 는 {@code private/*} 캐시 동작 하나로 가른다.
 *
 * <p><b>단일 파일 전용이다.</b> {@code thumb.jpg} 처럼 그 자체로 끝나는 주소에만 쓴다.
 * HLS 재생목록({@code master.m3u8})은 안에서 조각을 더 요청하므로 이 클래스로 만든
 * URL 을 그대로 내리면 조각 요청이 403 이 된다 — 재생목록은 내용을 다시 써서 조각
 * 주소마다 서명을 박아야 하고, 그건 별도 컴포넌트의 일이다.
 *
 * <p>공개 등급과 달리 <b>키가 없을 때 조용히 {@code null} 을 주지 않는다.</b>
 * {@link PublicMediaUrlResolver} 는 CDN 미설정 로컬을 위해 그렇게 하지만, 여기서
 * 같은 선택을 하면 "보호돼야 할 것이 URL 없이 안 보인다"와 "설정이 빠졌다"가 구분되지
 * 않는다. 키가 비어 있으면 {@link CloudFrontUrlSigner} 가 던진다.
 */
@Component
public class ProtectedMediaUrlResolver {

    private final String baseUrl;
    private final CloudFrontUrlSigner signer;

    public ProtectedMediaUrlResolver(@Value("${motif.media.public-base-url:}") String baseUrl,
                                     CloudFrontUrlSigner signer) {
        // 공개 등급과 같은 배포를 쓴다 — 보호 여부는 도메인이 아니라 경로(private/)로 가른다
        this.baseUrl = baseUrl;
        this.signer = signer;
    }

    /** 키가 없으면 {@code null}(항목 자체가 없는 경우). 있으면 서명된 절대 URL. */
    public String resolve(String key) {
        if (key == null || key.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        // 이미지용 만료를 쓴다 — 썸네일·프로필은 만료돼도 재생목록처럼 다시 받아
        // 복구되지 않는다(<img> 가 403 을 받으면 깨진 채 남는다)
        return signer.signImage(absolute(key));
    }

    /**
     * 단일 영상 파일 — <b>짧은 만료</b>로 서명한다 (0020, §7.6 편집본).
     *
     * <p>{@link #resolve} 와 나누는 이유는 <b>복구 경로가 있기 때문이다</b>. 이미지에
     * 긴 만료를 준 근거는 "403 을 받으면 깨진 채 남는다" 였는데, 이쪽은 만료되면 앱이
     * §7.6 을 다시 불러 새 URL 을 받는다 — 재생목록과 같은 모양이라 만료를 길게 잡을
     * 이유가 없고, 그만큼 URL 이 새어 나갔을 때 열려 있는 창이 짧아진다.
     *
     * <p>HLS 가 아니라 <b>통짜 MP4</b> 라서 여기 있다. 재생목록이면 조각마다 서명이
     * 따라붙어야 해 {@code signPrefix} 를 써야 한다.
     */
    public String resolveVideo(String key) {
        if (key == null || key.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return signer.sign(absolute(key));
    }

    private String absolute(String key) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/" + key;
    }
}

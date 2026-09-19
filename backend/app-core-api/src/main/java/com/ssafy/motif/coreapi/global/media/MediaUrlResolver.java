package com.ssafy.motif.coreapi.global.media;

import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * S3 키 → 재생·표시용 URL. <b>등급을 가르는 유일한 지점이다</b> (API 명세 §1.7).
 *
 * <p>{@link PublicMediaUrlResolver} 와 {@link ProtectedMediaUrlResolver} 는 각자
 * "무서명 URL 을 만든다" · "서명 URL 을 만든다" 만 안다. <b>둘 중 무엇을 쓸지</b>를
 * 호출부마다 정하면 판정이 화면 수만큼 늘어나고, 한 곳만 고쳤을 때 조용히 어긋난다 —
 * 보호돼야 할 썸네일이 무기명 링크로 새거나, 공개 썸네일이 403 으로 깨진다. 둘 다
 * 예외도 로그도 남기지 않는 종류의 사고다.
 *
 * <p>판정 근거는 {@link MediaKeys#isProtected} 하나뿐이다 — {@code visibility} 를 보지
 * 않는 이유는 그쪽 javadoc 에 있다.
 *
 * <p><b>{@link #resolve} 는 단일 파일 전용이다.</b> 썸네일·프로필처럼 그 자체로 끝나는
 * 주소에만 쓴다. HLS 재생목록은 안에서 조각을 더 요청하므로 여기서 만든 URL 을 그대로
 * 내리면 조각 요청이 403 이 된다 — 재생목록은 {@link #resolvePlaylist} 다(ADR-0012).
 */
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final PublicMediaUrlResolver publicResolver;
    private final ProtectedMediaUrlResolver protectedResolver;
    private final CloudFrontUrlSigner signer;

    /** 키가 없으면 {@code null}(항목 자체가 없는 경우). */
    public String resolve(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return MediaKeys.isProtected(key)
                ? protectedResolver.resolve(key)
                : publicResolver.resolve(key);
    }

    /**
     * 마스터 재생목록 키 → <b>재생 주소</b> (ADR-0012).
     *
     * <p>{@link #resolve} 와 나누는 이유는 <b>HLS 가 단일 파일이 아니기 때문이다</b>.
     * 재생목록은 안에서 조각을 더 요청하는데 그 주소는 상대 경로라, URL 하나짜리 서명
     * ({@link #resolve})을 붙여 내리면 조각 요청이 전부 403 이 된다.
     *
     * <p>보호 등급이면 <b>프리픽스 전체에 통하는 서명</b>을 붙여 준다 — 클라이언트(OkHttp
     * 인터셉터)가 같은 파라미터를 조각·자식 재생목록에 붙여 요청한다. 공개 등급이면
     * 서명이 없다.
     *
     * <p><b>등급 판정은 여전히 키 프리픽스 하나다.</b> 호출부가 {@code visibility} 로
     * 가르지 않는다 — 참여 영상은 {@code visibility} 와 무관하게 전부 {@code private/}
     * 에 있으므로, 그 값으로 가르면 {@code PUBLIC} 영상에 무서명 URL 이 나가 403 이
     * 된다({@link MediaKeys#isProtected} javadoc).
     *
     * @param masterKey {@code …/master.m3u8} 로 끝나는 키
     */
    public String resolvePlaylist(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            return null;
        }
        // 주소 자체는 두 등급이 같은 CDN 배포다 — 보호 여부는 서명이 붙느냐로만 갈린다
        String url = publicResolver.resolve(masterKey);
        if (url == null || !MediaKeys.isProtected(masterKey)) {
            return url;
        }
        return url + "?" + signer.signPrefix(MediaKeys.prefixOf(masterKey));
    }
}

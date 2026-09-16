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
 * <p><b>단일 파일 전용이다.</b> 썸네일·프로필처럼 그 자체로 끝나는 주소에만 쓴다.
 * HLS 재생목록은 안에서 조각을 더 요청하므로 여기서 만든 URL 을 그대로 내리면 조각
 * 요청이 403 이 된다 — 그쪽은 {@code MediaController} 가 재생목록을 다시 써서 낸다.
 */
@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final PublicMediaUrlResolver publicResolver;
    private final ProtectedMediaUrlResolver protectedResolver;

    /** 키가 없으면 {@code null}(항목 자체가 없는 경우). */
    public String resolve(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return MediaKeys.isProtected(key)
                ? protectedResolver.resolve(key)
                : publicResolver.resolve(key);
    }
}

package com.ssafy.motif.domain.core.video;

/**
 * 참여 영상의 공개 범위 ({@code participation_videos.visibility} — DB SPEC v0.15 §19).
 *
 * <p><b>이 값으로 미디어의 보호 등급을 판정하지 않는다.</b> 둘은 다른 층이다 — 이
 * 값은 <b>앱 안에서 누가 볼 자격이 있는가</b>(인가 정책)이고, 등급은 <b>그 자격을
 * CDN 에 어떻게 전달하는가</b>(전송 수단)다(ADR-0013). 등급은 S3 키의 최상위
 * 프리픽스가 정한다({@code MediaKeys}).
 *
 * <p>참여 영상은 이 값과 무관하게 <b>전부 {@code private/} 에 있다</b>. 그래서 여기서
 * 등급을 읽으면 {@link #PUBLIC} 영상에 무서명 URL 이 나가 403 이 된다.
 *
 * <p>이 값이 정하는 것은 피드 노출 여부와, <b>재생목록 서명을 내줄 때의 인가 판단</b>
 * 이다 — {@link #PUBLIC} 이라고 서명을 생략하는 것이 아니라 누구에게나 내준다.
 */
public enum Visibility {

    /** 피드·타인 프로필에 노출된다. 재생목록은 누구에게나 내준다. */
    PUBLIC,

    /** 본인에게만 보인다. 재생목록도 소유자에게만 내준다. */
    PRIVATE
}

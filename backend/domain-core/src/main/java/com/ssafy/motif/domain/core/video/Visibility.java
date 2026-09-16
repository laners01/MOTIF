package com.ssafy.motif.domain.core.video;

/**
 * 참여 영상의 공개 범위 ({@code participation_videos.visibility} — DB SPEC v0.15 §19).
 *
 * <p><b>이 값으로 미디어의 보호 등급을 판정하지 않는다.</b> 등급은 S3 키의 최상위
 * 프리픽스가 정한다({@code MediaKeys}). {@link #PUBLIC} 게시는 산출물 트리를
 * {@code public/} 으로 <b>복사</b>할 뿐 원본 키를 바꾸지 않으므로(§1.7), 여기서
 * 등급을 읽으면 "PUBLIC 인데 키는 아직 {@code private/}" 인 행에서 어긋난다.
 *
 * <p>이 값이 정하는 것은 <b>누가 볼 수 있는가</b>다 — 피드 노출 여부와, 보호 등급
 * 재생목록을 내줄 때의 인가 판단.
 */
public enum Visibility {

    /** 피드·타인 프로필에 노출된다. 재생목록은 누구에게나 내준다. */
    PUBLIC,

    /** 본인에게만 보인다. 재생목록도 소유자에게만 내준다. */
    PRIVATE
}

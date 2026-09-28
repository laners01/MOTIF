package com.ssafy.motif.coreapi.domain.media;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 미디어 공통 에러 코드 (API 명세 §15 · 어드민 명세 §5).
 *
 * <p>{@code IMAGE_NOT_UPLOADED} 는 어드민 전용이 아니다 — 사용자 프로필 이미지 교체
 * (API §4.4)가 먼저 쓰는 코드이고 어드민 공지(§3.10)·배너(§3.11)가 <b>같은 코드를
 * 재사용</b>한다(어드민 §5 "이미지는 기존 {@code IMAGE_NOT_UPLOADED}"). 그래서
 * {@code AdminErrorCode} 가 아니라 미디어 도메인이 소유한다.
 *
 * <p>영상 키 부재는 {@code AdminErrorCode.MEDIA_NOT_UPLOADED} 로 갈린다 — 어드민 업로드
 * 화면만 쓰는 코드라 그쪽이 소유한다.
 */
@Getter
@RequiredArgsConstructor
public enum MediaErrorCode implements ErrorCode {

    /** 이미지 키의 S3 오브젝트 부재(HEAD 실패). {@code data} 에 빠진 키 목록을 싣는다. */
    IMAGE_NOT_UPLOADED(409, "IMAGE_NOT_UPLOADED", "업로드되지 않은 이미지입니다."),

    /**
     * 보호 등급 재생목록을 내줄 수 없다 (API 명세 §1.7).
     *
     * <p><b>거부와 부재를 한 코드로 합친다.</b> 잡이 없는 경우, 가공이 끝나지 않은 경우,
     * 남의 비공개 영상인 경우가 전부 이 404 다 — 403 으로 갈라 내면 "그 영상은 존재하고
     * 당신 것이 아니다"가 응답에서 읽힌다. §1.8 공통 비노출이 막으려는 것이 그것이고,
     * 재생목록 주소는 {@code jobUuid} 만 있으면 누구나 두드려 볼 수 있어 더 그렇다.
     */
    PLAYLIST_NOT_FOUND(404, "PLAYLIST_NOT_FOUND", "재생할 수 없는 영상입니다.");

    private final int status;
    private final String code;
    private final String message;
}

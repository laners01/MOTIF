package com.ssafy.motif.common.response;

import java.util.List;

/**
 * 비페이지 목록 응답의 {@code data} 구조 (API 명세 §5.2 · §5.4 · §5.9 · §4.8).
 *
 * <p>전량 반환이라 페이지 칸이 없다 — {@link PageResponse} 의 {@code page}·
 * {@code totalPages} 를 0 으로 채워 내보내면 클라이언트가 "1페이지짜리 페이지 목록"
 * 으로 오해한다.
 *
 * <p><b>배열을 {@code data} 에 바로 싣지 않는 이유</b>는 {@link PageResponse} 와 같다 —
 * 한 겹 감싸 두면 나중에 항목 외의 필드를 더해도 파싱이 깨지지 않는다.
 */
public record ListResponse<T>(List<T> list) {

    public static <T> ListResponse<T> of(List<T> list) {
        return new ListResponse<>(list);
    }
}

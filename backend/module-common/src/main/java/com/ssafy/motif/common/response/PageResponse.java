package com.ssafy.motif.common.response;

import java.util.List;

/**
 * offset 목록 봉투 (API 명세 §1.2 · 어드민 명세 §1.5).
 *
 * <p><b>어드민 목록은 전부 offset 이다</b> — 실시간 유입으로 페이지가 밀리는 문제가
 * 없다. 커서 목록(API 명세 §1.9 — 영상 피드·아티스트 공지)은 이 모양이 아니라
 * {@code prevCursor}·{@code nextCursor} 를 쓴다. 둘을 한 타입으로 합치지 않는다.
 *
 * <p>{@code Page} 를 받지 않고 값 4개를 받는다 — {@code module-common} 은 spring-data 에
 * 의존하지 않는다({@code build.gradle} 은 jakarta.persistence 하나뿐이고, 알림 서비스도
 * 이 모듈을 쓴다). 변환 한 줄은 호출부에 둔다.
 */
public record PageResponse<T>(
        List<T> list,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public static <T> PageResponse<T> of(List<T> list, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(list, page, size, totalElements, totalPages,
                page + 1 < totalPages, page > 0);
    }
}

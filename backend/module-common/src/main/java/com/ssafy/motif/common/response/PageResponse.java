package com.ssafy.motif.common.response;

import java.util.List;

/**
 * offset 페이지 응답의 {@code data} 구조 (API 명세 §1.2 — <b>고정 구조</b>).
 *
 * <p>어드민 목록은 전부 offset 이다(어드민 명세 §1.5) — 실시간 유입이 없어 페이지 밀림이
 * 문제되지 않는다. 커서 페이지네이션(§1.9)은 영상 목록 전용이라 여기 섞지 않는다.
 *
 * <p><b>배열을 {@code data} 에 바로 싣지 않는다.</b> 한 겹 감싸 두면 나중에 항목 외의
 * 필드를 더해도 클라이언트 파싱이 깨지지 않는다 — 어드민 화면의 {@code fillSelect} 가
 * {@code data.list} 를 꺼내 쓰는 것도 이 규약에 기대고 있다.
 *
 * <p><b>스프링 타입을 쓰지 않는다.</b> {@link ApiResponse} 와 같은 자리의 순수 응답
 * 계약이고, module-common 은 spring-data 에 의존하지 않는다. 페이지 계산
 * ({@code totalPages}·{@code hasNext})은 채우는 쪽의 {@code Page} 가 이미 해 준 값을
 * 옮겨 담는다 — 여기서 나눗셈을 다시 하지 않는다(경계에서 틀리기 쉬운 계산이다).
 *
 * @param page 0-base
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
}

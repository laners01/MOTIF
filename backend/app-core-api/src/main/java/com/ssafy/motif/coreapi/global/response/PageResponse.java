package com.ssafy.motif.coreapi.global.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * offset 페이지 응답 — API 명세 §1.2 "페이지 응답({@code data} 내부 구조 고정)".
 *
 * <p>{@link Page} 를 그대로 직렬화하지 않는 이유: Spring 의 {@code PageImpl} 은 JSON
 * 구조가 버전마다 흔들리고({@code pageable}·{@code sort} 중첩) 명세가 고정한 7개 필드와
 * 모양이 다르다. 안드로이드가 파싱하는 계약이므로 여기서 고정한다.
 *
 * <p><b>{@code ApiResponse} 옆(module-common)이 아니라 여기 있는 이유</b> — {@link Page}
 * 가 spring-data-commons 타입이다. 봉투 하나 때문에 최하위 모듈에 데이터 접근 스택을
 * 끌어들이면 app-notification 까지 따라온다(ADR-0004 의존 방향). 페이지 응답을 만드는
 * 것은 조회 API 가 있는 이 모듈뿐이다.
 *
 * <p>어드민 목록은 전부 offset 이다(어드민 명세 §1.5) — 실시간 유입 밀림이 문제되지
 * 않는 관리 화면이고 총 건수·페이지 번호가 보여야 한다. 사용자 피드의 커서 방식과는
 * 다른 규약이다.
 */
public record PageResponse<T>(
        List<T> list, int page, int size,
        long totalElements, int totalPages,
        boolean hasNext, boolean hasPrevious
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.hasNext(), page.hasPrevious());
    }

    /** 조회 행 → 응답 DTO 변환을 끼워 넣는다 — 페이지 메타는 그대로 둔다. */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return of(page.map(mapper));
    }
}

package com.ssafy.motif.coreapi.global.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 목록 응답의 고정 구조 (API 명세 §1.2 "성공 — 페이지 응답" · 어드민 명세 §1.5).
 *
 * <p>어드민 목록은 전부 <b>offset</b> 이다 — 실시간 유입으로 밀리는 피드가 아니라
 * 운영자가 페이지를 오가며 보는 표다.
 *
 * <p>{@code data} 에 배열을 그대로 싣지 않고 봉투 안에 한 겹 더 감싸는 이유는
 * {@link com.ssafy.motif.coreapi.admin.pass.dto.PassPriceListResponse} 와 같다 —
 * 항목 외 필드가 늘어도 클라이언트 파싱이 깨지지 않는다.
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

    /** §1.5 — {@code size} 최대 100. 기본 20 은 컨트롤러 파라미터 기본값이다. */
    public static final int MAX_SIZE = 100;

    /**
     * 요청 파라미터 → {@link Pageable} (§1.5).
     *
     * <p><b>정렬은 받지 않는다.</b> 목록 쿼리가 {@code order by} 를 고정으로 갖고 있고,
     * 네이티브 쿼리에 {@code Sort} 를 얹으면 별칭이 어긋나 런타임에 깨진다. 상한을
     * 넘긴 {@code size} 는 거절하지 않고 잘라낸다 — 운영 화면이 100 을 넘겨도
     * 표가 비지 않는 편이 낫다.
     */
    public static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE));
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext(), page.hasPrevious());
    }
}

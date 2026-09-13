package com.ssafy.motif.coreapi.global.response;

import com.ssafy.motif.common.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.function.Function;

/**
 * Spring Data {@link Page} ↔ {@link PageResponse} 어댑터.
 *
 * <p><b>왜 응답 record 옆이 아니라 여기인가</b> — {@link PageResponse} 는
 * {@code module-common} 의 순수 응답 계약이고 그 모듈은 spring-data 에 의존하지 않는다.
 * {@code Page} 를 받는 편의 메서드를 record 안에 넣으면 응답 계약이 spring-data 를
 * 끌고 들어오게 되므로, 변환은 spring-data 가 이미 있는 이쪽 모듈에 둔다.
 *
 * <p>페이지 계산({@code totalPages}·{@code hasNext})은 {@code Page} 가 이미 한 값을
 * 옮겨 담기만 한다 — 경계에서 틀리기 쉬운 나눗셈을 두 곳에 두지 않는다.
 *
 * <p>목록 API 4개(곡 §3.6 · 아티스트 §3.5 · 공지 §3.10 · 챌린지 §3.7 · 에셋 §3.8)가
 * 병렬로 만들어지면서 같은 변환을 각자 들고 있었다. 여기로 모은다.
 */
public final class PageResponses {

    /** §1.5 — {@code size} 최대 100. 기본값 20 은 컨트롤러의 {@code @RequestParam} 이 갖는다. */
    public static final int MAX_SIZE = 100;

    private PageResponses() {
    }

    /**
     * 목록 조회용 {@link Pageable} — 음수 page 와 과대 size 를 여기서 자른다.
     *
     * <p>정렬을 받지 않는다. 어드민 목록은 서버 고정 정렬이고(§1.5), {@code sort} 를
     * 열면 화이트리스트가 필요해진다 — 없으면 오타 하나가 500 이 된다.
     */
    public static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE));
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext(), page.hasPrevious());
    }

    /** 엔티티·프로젝션 페이지를 응답 DTO 로 옮길 때. */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return of(page.map(mapper));
    }
}

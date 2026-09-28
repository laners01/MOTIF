package com.ssafy.motif.common.response;

import java.util.List;

/**
 * 커서 페이지 응답의 {@code data} 구조 (API 명세 §1.9 — <b>고정 구조</b>).
 *
 * <p>실시간 유입으로 offset 페이지가 밀리거나 무한 스크롤을 쓰는 목록 전용이다 —
 * 4.9 내 활동 · <b>5.5 아티스트 공지</b> · 6.1 HOT · 7.1 챌린지 피드 · 8.1 참여 영상 ·
 * 11.5 보관함. 어드민 목록은 전부 offset 이라 {@link PageResponse} 를 쓴다. 둘을
 * 한 record 로 합치지 않는다 — 커서 목록에는 {@code totalElements}·{@code totalPages}
 * 가 존재하지 않고(세려면 전체를 훑어야 한다), 합치면 채울 수 없는 칸이 생긴다.
 *
 * <p>{@code nextCursor} 가 {@code null} 이면({@code hasNext=false}) 끝이다. 커서는 서버가
 * 만든 opaque 토큰이고 <b>클라이언트는 해석·조작하지 않는다</b> — 안에 무엇이 들었는지는
 * 서버만 안다(정렬 키가 바뀌어도 계약이 안 깨지는 이유다).
 *
 * <p>{@code prevCursor}·{@code hasPrevious} 는 <b>자리만 잡아 둔 칸이다</b>. §1.9 는
 * 역방향 조회를 명시했다가 같은 절에서 "현재 계약은 {@code nextCursor} 기준 순방향
 * 조회만 확정한다"로 좁혔고, 제공 여부 자체가 §16 미결 안건이다. 지금은 순방향
 * 구현이라 항상 {@code null}·{@code false} 로 내려간다 — 안드로이드가 필드 부재로
 * 파싱이 깨지지 않도록 칸은 유지한다.
 *
 * <p><b>배열을 {@code data} 에 바로 싣지 않는다</b> — {@link PageResponse} 와 같은 근거다.
 *
 * @param prevCursor 순방향 구현에서는 항상 {@code null} (위 참조)
 * @param nextCursor 다음 페이지 요청에 그대로 넣을 토큰. 끝이면 {@code null}
 */
public record CursorResponse<T>(
        List<T> list,
        String prevCursor,
        String nextCursor,
        boolean hasPrevious,
        boolean hasNext
) {

    /** 순방향 전용 생성 — 역방향이 확정되기 전까지 호출부가 쓰는 유일한 통로다. */
    public static <T> CursorResponse<T> forward(List<T> list, String nextCursor) {
        return new CursorResponse<>(list, null, nextCursor, false, nextCursor != null);
    }
}

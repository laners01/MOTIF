package com.ssafy.motif.coreapi.domain.comment;

import java.util.Arrays;
import java.util.Optional;

/**
 * 개별 댓글 경로의 {@code {type}} (API 명세 §9) — 댓글 테이블 3종과 1:1.
 *
 * <p>목록·작성(§9.1·§9.3)은 대상 리소스 하위 경로를 쓰지만, <b>개별 조작</b>
 * (§9.2 답글 · §9.4 삭제 · §9.5 좋아요 · §9.6 신고)은 {@code commentSeq} 만 들고
 * {@code /comments/{type}/{commentSeq}} 로 들어온다 — 댓글 PK 는 테이블마다 따로 도는
 * 시퀀스라 <b>{@code type} 없이는 어느 테이블의 501 인지 알 수 없다</b>.
 *
 * <p><b>경로에는 소문자로 온다</b>({@code challenge}·{@code video}·{@code notice}).
 * 스프링 기본 변환기는 대소문자를 가리므로 {@link #from} 이 직접 푼다 — 변환기를
 * 전역에 등록하면 이 enum 하나 때문에 모든 경로 변수의 변환 규칙이 바뀐다.
 */
public enum CommentTargetType {

    CHALLENGE("challenge"),
    VIDEO("video"),
    NOTICE("notice");

    private final String path;

    CommentTargetType(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    /**
     * 경로 변수 → 타입. <b>모르는 값은 비어 있다</b>.
     *
     * <p>호출부가 그것을 {@code COMMENT_NOT_FOUND}(404) 로 답한다 —
     * {@code INVALID_PARAMETER}(400) 로 가르면 오타 한 번에 "그런 종류는 있다/없다" 가
     * 드러나서, 존재를 숨기려는 §1.8 과 어긋난다.
     */
    public static Optional<CommentTargetType> from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.path.equals(value))
                .findFirst();
    }
}

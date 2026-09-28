package com.ssafy.motif.coreapi.admin.song.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 곡 등록 (어드민 명세 §3.6.2).
 *
 * <p><b>중복을 막지 않는다</b> — 리믹스·재녹음이 같은 제목을 갖는다(DB 에 UNIQUE 없음).
 * 화면이 등록 전 동일 곡명·가수명 존재를 경고만 하고, 서버는 그대로 받는다.
 *
 * <p>길이 상한은 DB {@code VARCHAR(100)} 과 같은 값이다 — 넘치면 DB 예외로 500 이 나므로
 * 여기서 400 으로 먼저 막는다.
 */
@Schema(description = "곡 등록")
public record SongCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 100) String singerName) {
}

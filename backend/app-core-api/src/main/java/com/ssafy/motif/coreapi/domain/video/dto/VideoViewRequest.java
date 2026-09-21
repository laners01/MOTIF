package com.ssafy.motif.coreapi.domain.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * 조회 이벤트 요청 (API 명세 §8.5).
 *
 * <p><b>영상 하나가 아니라 묶음이다</b>(2026-09-21 재정의 — ADR-0015). 뷰어가 세로
 * 스와이프로 영상을 연속 소비하는 화면이라 한 세션에 조회가 여러 건 쌓이는데, 건마다
 * 왕복하면 재생 중에 네트워크를 계속 쓴다.
 *
 * <p>JSON 객체의 키라 {@code videoSeq} 는 문자열로 온다 — Jackson 이 {@code Long} 으로
 * 바꾸고, 숫자가 아니면 본문 해석 실패로 400 이다.
 *
 * <p><b>상한 검증이 여기 없다.</b> 항목 수와 건당 조회수 상한이 설정값이라
 * {@code @Size}·{@code @Max} 같은 컴파일 상수 애너테이션으로 표현할 수 없다.
 * {@code VideoViewService} 가 {@code VideoViewProperties} 를 보고 판정한다 — 두 곳에
 * 나눠 두면 한쪽만 고쳤을 때 상한이 조용히 갈린다.
 *
 * @param views {@code videoSeq → 증가 조회수}
 */
public record VideoViewRequest(

        @NotEmpty(message = "views 는 비어 있을 수 없다")
        @Schema(description = "videoSeq → 증가 조회수", example = "{\"101\": 3, \"102\": 1}")
        Map<Long, Integer> views
) {
}

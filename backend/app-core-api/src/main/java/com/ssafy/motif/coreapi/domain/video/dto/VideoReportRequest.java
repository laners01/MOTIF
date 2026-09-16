package com.ssafy.motif.coreapi.domain.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 참여 영상 신고 요청 (API 명세 §8.7).
 *
 * @param reasonCode 신고 사유. <b>코드표가 아직 없다</b>(원본 §16 #4 — §8.7·§9.6·§13.3
 *                   공통 미결)이라 <b>문자열로 받는다</b>. 값을 지금 enum 으로 고정하면
 *                   회의가 표를 정하는 순간 이미 쌓인 행이 열거에 없는 값을 갖게 되고,
 *                   역직렬화가 그때부터 400 을 낸다. 서버가 보는 것은 컬럼 폭
 *                   ({@code VARCHAR(30)})과 공백 여부뿐이다
 */
@Schema(description = "영상 신고 요청 (§8.7)")
public record VideoReportRequest(

        @Schema(description = "신고 사유. 코드표는 미결이라 문자열이다", example = "SPAM")
        @NotBlank
        @Size(max = 30)
        String reasonCode
) {
}

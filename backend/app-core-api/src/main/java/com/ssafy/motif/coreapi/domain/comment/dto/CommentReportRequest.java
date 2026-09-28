package com.ssafy.motif.coreapi.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 신고 요청 (API 명세 §9.6).
 *
 * <p><b>§8.7 {@code VideoReportRequest} 와 모양이 같지만 합치지 않는다.</b> 그쪽은 §8
 * 도메인의 record 이고, 두 API 가 한 파일을 나눠 가지면 사유 코드표가 정해질 때
 * (원본 §16 #4) 한쪽 요구만 반영해도 다른 쪽이 끌려간다.
 *
 * @param reasonCode 신고 사유. <b>코드표가 아직 없다</b>(원본 §16 #4 — §8.7·§9.6·§13.3
 *                   공통 미결)이라 <b>문자열로 받는다</b>. 지금 enum 으로 고정하면 표가
 *                   정해지는 순간 이미 쌓인 행이 열거에 없는 값을 갖게 되고, 역직렬화가
 *                   그때부터 400 을 낸다. 서버가 보는 것은 컬럼 폭({@code VARCHAR(30)})과
 *                   공백 여부뿐이다
 */
@Schema(description = "댓글 신고 요청 (§9.6)")
public record CommentReportRequest(

        @Schema(description = "신고 사유. 코드표는 미결이라 문자열이다", example = "SPAM")
        @NotBlank
        @Size(max = 30)
        String reasonCode
) {
}

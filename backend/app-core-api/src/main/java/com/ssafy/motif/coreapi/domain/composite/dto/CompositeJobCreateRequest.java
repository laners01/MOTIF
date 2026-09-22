package com.ssafy.motif.coreapi.domain.composite.dto;

import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.domain.core.job.JobType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.ArrayList;
import java.util.List;

/**
 * 합성 잡 생성 요청 (API 명세 §13.1) — {@code jobType} 에 따라 두 형태다.
 *
 * <p>형태 검증은 {@link #shapeErrors()} 다. 필수·금지가 {@code jobType} 값에 따라 갈려
 * 필드 애너테이션 하나로 표현되지 않으므로, 서비스가 이 목록을 400
 * {@code INVALID_PARAMETER} 의 {@code data} 로 그대로 내보낸다 — {@code OrderCreateRequest}
 * 와 같은 봉투다(README §1.2).
 *
 * <p><b>{@code challengeParticipantSeq} 만 예외적으로 무시한다.</b> 반대 형태의 칸
 * ({@code challengeSeq}·{@code assetSeq})은 거절하는데 이것만 받아 넘기는 이유는
 * MOTIF 분기의 명시적 계약이기 때문이다(S15P21D104-246 — "받아도 무시").
 *
 * @param jobType                 {@code CHALLENGE} | {@code MOTIF}
 * @param challengeSeq            CHALLENGE 일 때 필수 — 대상 챌린지
 * @param assetSeq                MOTIF 일 때 필수 — 대상 에셋
 * @param challengeParticipantSeq CHALLENGE 의 교체 슬롯. <b>필수다</b>(2026-09-22) —
 *                                합성은 언제나 슬롯 한 자리를 대체하는 것이고, 원본 위에
 *                                얹는 무교체 합성은 없어졌다. 0020 이전에는
 *                                {@code participantMask} 비트값이었는데, 앱이 비트 연산을
 *                                하게 두지 않으려고 §7.5 가 준 seq 를 그대로 받는 형태로
 *                                바꿨다 — {@code 0} 이라는 예약값도 그때 사라졌다
 * @param contentLength   올릴 촬영본의 바이트 수. <b>이 값이 presigned URL 서명에 박힌다</b> —
 *                        SigV4 의 서명된 {@code Content-Length} 는 상한이 아니라 <b>정확히
 *                        일치</b>해야 하므로, 서버가 상한값을 대신 넣으면 그 크기 파일만
 *                        올라간다(2026-09-19 에 실제로 그랬다). 어드민 업로드(§1.4)·프로필
 *                        업로드(§4.3)와 같은 방식이다
 */
@Schema(description = "합성 잡 생성 요청 (§13.1) — CHALLENGE 와 MOTIF 두 형태")
public record CompositeJobCreateRequest(

        @Schema(description = "잡 종류", example = "CHALLENGE")
        @NotNull
        JobType jobType,

        @Schema(description = "CHALLENGE 일 때 필수 — 대상 챌린지. MOTIF 면 보내지 않는다",
                example = "11")
        Integer challengeSeq,

        @Schema(description = "MOTIF 일 때 필수 — 대상 에셋. CHALLENGE 면 보내지 않는다",
                example = "21")
        Integer assetSeq,

        @Schema(description = """
                교체할 슬롯 — §7.5 가 준 challengeParticipantSeq 를 그대로 보낸다.
                CHALLENGE 에 필수다. MOTIF 에서는 무시된다""",
                example = "31")
        @Positive
        Integer challengeParticipantSeq,

        @Schema(description = """
                올릴 촬영본의 바이트 수. 이 값이 서명에 박히므로 PUT 의 Content-Length 와
                정확히 같아야 한다 — 다르면 S3 가 403 으로 거부한다""",
                example = "20971520")
        @Positive
        long contentLength
) {

    /**
     * 잡 형태 검증 — DB CHECK {@code ck_composite_jobs_type_shape} 와 같은 모양이다.
     *
     * <p>반대 형태의 칸을 <b>무시하지 않고 거절하는</b> 이유는 {@code OrderCreateRequest}
     * 와 같다 — 무시하면 "MOTIF 인데 challengeSeq 를 보냈다" 는 클라이언트 버그가 조용히
     * 통과해 엉뚱한 대상이 합성된다. DB 도 두 칸이 함께 차는 것을 막는다.
     *
     * @return 위반 필드 목록. 비어 있으면 형태가 맞다. {@code jobType} 이 없으면 비어
     *         있다 — 그쪽은 {@code @NotNull} 이 잡는다
     */
    public List<FieldErrorResponse> shapeErrors() {
        if (jobType == null) {
            return List.of();
        }
        List<FieldErrorResponse> errors = new ArrayList<>();
        switch (jobType) {
            case CHALLENGE -> {
                required(errors, "challengeSeq", challengeSeq);
                // 2026-09-22 — 합성은 언제나 슬롯 한 자리를 대체한다. 무교체가 없어지면서
                // 이 칸이 필수가 됐다. 생략을 허용하던 시절에는 원본 위 합성으로 읽혔다
                required(errors, "challengeParticipantSeq", challengeParticipantSeq);
                forbidden(errors, "assetSeq", assetSeq);
            }
            case MOTIF -> {
                required(errors, "assetSeq", assetSeq);
                forbidden(errors, "challengeSeq", challengeSeq);
            }
        }
        return List.copyOf(errors);
    }

    private void required(List<FieldErrorResponse> errors, String field, Object value) {
        if (value == null) {
            errors.add(new FieldErrorResponse(field, jobType + " 잡에 필수입니다."));
        }
    }

    private void forbidden(List<FieldErrorResponse> errors, String field, Object value) {
        if (value != null) {
            errors.add(new FieldErrorResponse(field, jobType + " 잡에는 보내지 않습니다."));
        }
    }
}

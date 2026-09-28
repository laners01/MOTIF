package com.ssafy.motif.coreapi.domain.composite.dto;

import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 잡 상태·결과 (API 명세 §13.3) — <b>공통부 + {@code jobType} 별 분기 필드</b>.
 *
 * <p><b>두 모양을 한 레코드로 합치지 않는다.</b> 명세의 MOTIF 응답에는 {@code resultVideoUrl}
 * 칸이 <b>아예 없고</b>, CHALLENGE 진행 중 응답에는 그 칸이 {@code null} 로 <b>있다</b>.
 * 한 레코드에 다 넣고 {@code null} 을 지우면 CHALLENGE 진행 중 응답에서 칸이 사라지고,
 * 안 지우면 MOTIF 응답에 없어야 할 칸이 생긴다 — 둘 다 문서와 어긋난다.
 *
 * <p><b>잡이 실패해도 조회는 200 이다.</b> {@code status} 로 판단하고 {@code failCode} 로
 * 안내한다. 진행 중·실패면 분기 필드가 전부 {@code null} 이다.
 */
public sealed interface CompositeJobStatusResponse {

    /**
     * CHALLENGE — 게시 전 프리뷰라 <b>보호 등급</b>이다(§1.7).
     *
     * @param resultVideoUrl     core-api 가 서명해 다시 쓴 HLS 마스터 재생목록.
     *                           {@code DONE} 이 아니면 {@code null}
     * @param resultThumbnailUrl 서명된 단일 파일 주소. {@code DONE} 이 아니면 {@code null}
     */
    @Schema(description = "잡 상태·결과 (§13.3) — CHALLENGE")
    record Challenge(
            @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID jobUuid,
            @Schema(example = "CHALLENGE") JobType jobType,
            @Schema(example = "DONE") JobStatus status,
            @Schema(description = "시도 횟수 — 재시도(§13.4)마다 증가", example = "1") short attempt,
            @Schema(description = "실패 사유. FAILED 가 아니면 null", example = "FACE_NOT_DETECTED")
            String failCode,
            @Schema(example = "https://cdn.motif.io/private/challenge/3fa85f64.../master.m3u8?Policy=...")
            String resultVideoUrl,
            @Schema(example = "https://cdn.motif.io/private/challenge/3fa85f64.../thumb.jpg?Policy=...")
            String resultThumbnailUrl) implements CompositeJobStatusResponse {
    }

    /**
     * MOTIF — <b>URL 을 싣지 않는다</b>. 재생과 문구 입력은 §11.6·§11.8 이 담당하므로
     * {@code resultSeq} 로 보관함 상세로 보낸다(R8 확정).
     *
     * @param resultSeq {@code DONE} 이면 <b>항상 존재한다</b> — 결과 행 생성이 실패하면 잡을
     *                  {@code DONE} 으로 만들지 않는다(§13.3 "MOTIF 잡의 완료 순서")
     */
    @Schema(description = "잡 상태·결과 (§13.3) — MOTIF")
    record Motif(
            @Schema(example = "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f") UUID jobUuid,
            @Schema(example = "MOTIF") JobType jobType,
            @Schema(example = "DONE") JobStatus status,
            @Schema(example = "1") short attempt,
            @Schema(example = "FACE_NOT_DETECTED") String failCode,
            @Schema(description = "보관함 결과물 seq — §11.6 으로 이동한다", example = "10")
            Long resultSeq) implements CompositeJobStatusResponse {
    }
}

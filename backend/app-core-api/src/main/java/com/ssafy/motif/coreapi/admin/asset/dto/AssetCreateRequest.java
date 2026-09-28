package com.ssafy.motif.coreapi.admin.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 에셋 등록 요청 (어드민 명세 §3.8.2).
 *
 * <p>{@code durationMs}·{@code width}·{@code height} 는 어드민이 입력하는 값이 아니라
 * <b>업로드 화면 JS 가 {@code <video>} 에서 읽어</b> 폼에 실어 보낸 값이다 — core-api 에는
 * ffmpeg 가 없다(§1.4, v0.5). 서버는 {@code VideoRules} 로 대조만 한다.
 *
 * @param thumbnailAtMs 썸네일로 쓸 시각(ms). <b>선택 — 없으면 워커 기본값 1500</b>.
 *                      챌린지 원본(§4.8)과 같은 값이고 저장하지도 않는다 — 워커로 가는
 *                      일회성 지시라 {@code motif_assets} 에 칸이 없다
 * @param price KR 판매가. <b>필수다</b> — 가격 없는 에셋이 활성 상태로 남는 것을 막는다
 *              (S15P21D104-568). 명세 §3.8.2 는 선택으로 적혀 있고 §8 #7 이 아직 미결이라,
 *              "허용" 으로 확정되면 {@code @NotNull} 만 떼면 된다
 */
public record AssetCreateRequest(
        @NotNull Integer artistSeq,
        @NotBlank @Size(max = 100) String title,
        String description,
        @NotNull AssetType assetType,
        @NotBlank String videoKey,
        @Positive long durationMs,
        @Positive int width,
        @Positive int height,
        Long thumbnailAtMs,
        @NotNull @Valid AssetPriceRequest price
) {
}

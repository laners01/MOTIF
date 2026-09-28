package com.ssafy.motif.coreapi.admin.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 에셋 수정 요청 (어드민 명세 §3.8.4) — <b>부분 수정</b>이다. 보낸 필드만 반영한다.
 *
 * <p>{@code artistSeq} 는 받지 않는다. 소속 아티스트를 옮기면 이미 팔린 구매
 * ({@code asset_purchases})와 만들어진 결과물({@code motif_results})이 다른 아티스트
 * 아래로 따라가 운영 지표가 뒤틀린다 — 명세 §3.8.4 수정 항목에도 없다.
 *
 * <p>{@code videoKey} 를 보내면 <b>{@code durationMs}·{@code width}·{@code height} 도
 * 함께 와야 한다</b>({@link #hasVideo()}). core-api 에는 ffmpeg 가 없어 서버가 영상을
 * 열어 볼 수 없고(v0.5 — {@code MediaProbeService} 삭제), 화면 JS 가 {@code <video>} 로
 * 읽어 실어 보낸 값을 {@code VideoRules} 로 대조만 한다. 등록(§3.8.2)과 같은 규약이다.
 *
 * <p>영상을 바꿔도 <b>기존 결과물·구매는 영향받지 않는다</b> — 그것들은 합성 시점의
 * 키를 따로 들고 있다(§3.8.4).
 *
 * @param thumbnailAtMs 교체본에서 썸네일을 뽑을 시각(ms). <b>선택 — 없으면 워커 기본값
 *                      1500</b>. {@code videoKey} 없이 이것만 보내는 것은 아무 일도 하지
 *                      않는다 — 후처리는 영상 교체 분기에서만 발행되고, 이미 가공이 끝난
 *                      에셋의 썸네일만 다시 뽑는 길은 아직 없다(워커의 멱등 가드가
 *                      막는다 — {@code MediaJobProcessor.alreadyDone})
 */
public record AssetUpdateRequest(
        @Size(max = 100) String title,
        String description,
        AssetType assetType,
        String videoKey,
        @Positive Long durationMs,
        @Positive Integer width,
        @Positive Integer height,
        Long thumbnailAtMs
) {

    /** 영상 교체 요청인가. 넷이 모두 있어야 성립한다 — 부분만 오면 서버가 대조할 수 없다. */
    public boolean hasVideo() {
        return videoKey != null && !videoKey.isBlank();
    }

    /** 영상 교체인데 메타가 빠졌는가. {@code true} 면 {@code INVALID_PARAMETER} 다. */
    public boolean videoMetaMissing() {
        return hasVideo() && (durationMs == null || width == null || height == null);
    }
}

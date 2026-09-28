package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 영상 규격 검증 (어드민 명세 §1.4 검증 2항) — S15P21D104-589.
 *
 * <p><b>여기서 ffmpeg 를 돌리지 않는다.</b> core-api 이미지에는 바이너리가 없다(v0.5 §0-2).
 * 길이·해상도는 업로드 화면의 JS 가 {@code <video>} 의 {@code onloadedmetadata} 로 읽어
 * 폼에 실어 보낸 값이고, 이 클래스는 그 값을 규칙과 대조만 한다. 인코딩·썸네일·렌디션은
 * 전부 app-media 소유다 — 파라미터를 이쪽에 복사하면 화질 간 키프레임 정렬이 어긋나
 * <b>예외도 로그도 없이 화질 전환만</b> 안 된다(ADR-0010 D4).
 *
 * <p>값의 출처가 클라이언트라 개발자 도구로 조작할 수 있다. 어드민은 테일넷 한정·시드
 * 계정·팀원 수 명이라 위험이 낮고, 실제 바이트를 읽는 app-media 가 나중에 실측값으로
 * 정정할 수 있다(§8 #20). 이 검증의 목적은 방어가 아니라 <b>운영 실수 차단</b>이다.
 *
 * <p>순수 계산만 한다(I/O·DB 없음) — 경계값 단위 테스트로 규칙을 고정한다(589 DoD).
 * 위반은 한 건 찾고 멈추지 않고 <b>전부 모아서</b> 던진다. 어드민이 한 번에 고칠 수 있다.
 */
@Component
@RequiredArgsConstructor
public class VideoRules {

    /** 챌린지 원본은 세로여야 한다(h > w) — 피드가 세로 화면이다. */
    public static final String PORTRAIT_REQUIRED = "PORTRAIT_REQUIRED";

    /** 에셋 미리보기는 가로여야 한다(w > h) — 숍 화면 카드가 가로다. */
    public static final String LANDSCAPE_REQUIRED = "LANDSCAPE_REQUIRED";

    /** 최대 길이 초과. */
    public static final String DURATION_EXCEEDED = "DURATION_EXCEEDED";

    /** 길이가 0 이하 — 화면이 메타데이터를 못 읽었거나 폼이 조작됐다. */
    public static final String DURATION_INVALID = "DURATION_INVALID";

    /** 가로·세로가 0 이하. */
    public static final String DIMENSION_INVALID = "DIMENSION_INVALID";

    /**
     * 썸네일 시각이 0 미만이거나 영상 길이 이상 (§4.8).
     *
     * <p>막지 않으면 ffmpeg 가 그 지점에서 <b>빈 프레임</b>을 뱉는다 — 예외도 로그도
     * 없이 까만 썸네일이 피드에 올라간다.
     */
    public static final String THUMBNAIL_AT_OUT_OF_RANGE = "THUMBNAIL_AT_OUT_OF_RANGE";

    private final AdminMediaProperties properties;

    /**
     * 업로드 화면이 보낸 영상 메타 (명세 §1.4 · §3.7.2 폼 필드).
     *
     * @param durationMs {@code video.duration}(초)에 1000 을 곱한 값
     */
    public record Meta(long durationMs, int width, int height) {
    }

    /** 챌린지 원본 — 세로 · 최대 길이 이내 (§1.4). */
    public void checkChallengeOrigin(Meta meta) {
        checkChallengeOrigin(meta, null);
    }

    /**
     * 챌린지 원본 — 세로 · 최대 길이 이내 (§1.4) · 썸네일 시각이 영상 안 (§4.8).
     *
     * @param thumbnailAtMs 선택 값이라 null 이면 검사하지 않는다 — 워커가 기본값 1500 을 쓴다
     */
    public void checkChallengeOrigin(Meta meta, Long thumbnailAtMs) {
        List<String> reasons = baseReasons(meta);
        if (meta.width() > 0 && meta.height() > 0 && meta.height() <= meta.width()) {
            reasons.add(PORTRAIT_REQUIRED);
        }
        if (meta.durationMs() > properties.rules().challengeMaxDuration().toMillis()) {
            reasons.add(DURATION_EXCEEDED);
        }
        addThumbnailAtReason(reasons, meta, thumbnailAtMs);
        reject(reasons, "챌린지 원본 규격 위반");
    }

    /** 모티프 에셋 — 가로 · 최대 길이 이내 (§1.4). */
    public void checkAsset(Meta meta) {
        checkAsset(meta, null);
    }

    /**
     * 모티프 에셋 — 가로 · 최대 길이 이내 (§1.4) · 썸네일 시각이 영상 안 (§4.8).
     *
     * <p>썸네일 규칙이 챌린지와 <b>같다</b>. 방향이 반대라 규격 검사는 갈리지만, 썸네일
     * 시각은 영상 길이만 보는 값이라 방향과 무관하다 — 그래서 검사를 복사하지 않고
     * {@link #addThumbnailAtReason} 하나를 공유한다.
     *
     * @param thumbnailAtMs 선택 값이라 null 이면 검사하지 않는다 — 워커가 기본값 1500 을 쓴다
     */
    public void checkAsset(Meta meta, Long thumbnailAtMs) {
        List<String> reasons = baseReasons(meta);
        if (meta.width() > 0 && meta.height() > 0 && meta.width() <= meta.height()) {
            reasons.add(LANDSCAPE_REQUIRED);
        }
        if (meta.durationMs() > properties.rules().assetMaxDuration().toMillis()) {
            reasons.add(DURATION_EXCEEDED);
        }
        addThumbnailAtReason(reasons, meta, thumbnailAtMs);
        reject(reasons, "에셋 영상 규격 위반");
    }

    /**
     * 편집본 길이 = 원본 ±허용 오차 (§1.4).
     *
     * <p>{@code MEDIA_INVALID} 가 아니라 {@code EDITED_DURATION_MISMATCH}(409)를 쓴다 —
     * 명세 §5 가 3.7.2/5/6 의 이 분기에 전용 코드를 배정했다. 영상 자체는 멀쩡하고
     * <b>원본과의 관계</b>가 틀린 것이라 상태 충돌(409)이 맞다.
     */
    public void checkEditedDuration(long editedDurationMs, long originDurationMs) {
        long tolerance = properties.rules().editedTolerance().toMillis();
        long diff = Math.abs(editedDurationMs - originDurationMs);
        if (diff > tolerance) {
            throw new BusinessException(AdminErrorCode.EDITED_DURATION_MISMATCH,
                    "편집본 길이 불일치: edited=%dms origin=%dms diff=%dms tolerance=%dms"
                            .formatted(editedDurationMs, originDurationMs, diff, tolerance),
                    Map.of("editedDurationMs", editedDurationMs,
                            "originDurationMs", originDurationMs, "toleranceMs", tolerance));
        }
    }

    /** 썸네일 시각이 영상 안인가 (§4.8) — 챌린지 원본·에셋 미리보기 공용. */
    private static void addThumbnailAtReason(List<String> reasons, Meta meta, Long thumbnailAtMs) {
        // durationMs 와 같다면 마지막 프레임 다음이라 이미 영상 밖이다 — 경계는 배타적이다
        if (thumbnailAtMs != null
                && (thumbnailAtMs < 0 || thumbnailAtMs >= meta.durationMs())) {
            reasons.add(THUMBNAIL_AT_OUT_OF_RANGE);
        }
    }

    private static List<String> baseReasons(Meta meta) {
        List<String> reasons = new ArrayList<>();
        if (meta.durationMs() <= 0) {
            reasons.add(DURATION_INVALID);
        }
        if (meta.width() <= 0 || meta.height() <= 0) {
            reasons.add(DIMENSION_INVALID);
        }
        return reasons;
    }

    private static void reject(List<String> reasons, String context) {
        if (!reasons.isEmpty()) {
            throw new BusinessException(AdminErrorCode.MEDIA_INVALID,
                    context + ": " + reasons, reasons);
        }
    }
}

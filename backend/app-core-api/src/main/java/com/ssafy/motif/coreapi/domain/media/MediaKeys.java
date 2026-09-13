package com.ssafy.motif.coreapi.domain.media;

import com.ssafy.motif.event.contract.MediaJobRequestedEvent;

/**
 * 산출물 S3 키 규약 — <b>한 곳에서만 정한다</b>.
 *
 * <p>app-media 는 이 프리픽스 아래에 {@code master.m3u8}·{@code thumb.jpg}·화질별
 * 디렉터리를 깐다. DB 에는 마스터 재생목록 경로 하나만 저장하고 나머지는 이 규약으로
 * 찾으므로(ADR-0010 D4), 규약이 두 군데로 갈라지면 조각을 못 찾는다.
 */
public final class MediaKeys {

    private MediaKeys() {
    }

    /**
     * 후처리 산출물이 깔릴 프리픽스.
     *
     * <pre>
     * COMPOSITE_RESULT → results/composite/&lt;job_seq&gt;
     * CHALLENGE_ORIGIN → challenges/&lt;challenge_seq&gt;
     * </pre>
     */
    public static String outputPrefix(String sourceType, long sourceSeq) {
        return switch (sourceType) {
            case MediaJobRequestedEvent.SOURCE_COMPOSITE_RESULT -> "results/composite/" + sourceSeq;
            case MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN -> "challenges/" + sourceSeq;
            default -> throw new IllegalArgumentException("알 수 없는 sourceType: " + sourceType);
        };
    }
}

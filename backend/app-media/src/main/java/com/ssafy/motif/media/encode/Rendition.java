package com.ssafy.motif.media.encode;

/**
 * 화질 사다리의 한 단 (ADR-0010 D1).
 *
 * <p><b>{@code height} 는 ffmpeg 의 출력 높이다</b> — {@code scale=-2:height} 로
 * 그대로 들어가고 너비는 비율에 맞춰 따라온다. 세로(9:16) 영상에서는 <b>긴 변</b>이
 * 되며, 720 단은 406×720 이 된다(실측). 짧은 변이 아니다 — 이 단의 이름 {@code 720p}
 * 도 짧은 변 406 이 아니라 이 높이를 가리킨다.
 *
 * @param tag         S3 키·재생목록 경로에 쓰는 이름 ({@code 720p})
 * @param height      출력 높이 픽셀 (세로 영상에서는 긴 변)
 * @param maxrateKbps 비트레이트 상한. CRF 만 쓰면 내용에 따라 원본 대비 15~69% 로
 *                    4.6배 편차가 난다 — ABR 이 재생목록의 {@code BANDWIDTH} 로
 *                    화질을 고르므로 사다리가 예측 가능해야 한다 (ADR-0010 D4)
 */
public record Rendition(String tag, int height, int maxrateKbps) {

    /**
     * 채택 사다리 (ADR-0010 D1). 낮은 화질부터 — 마스터 재생목록 순서와 같다.
     *
     * <p>2026-09-14 에 1080p 를 더해 3단이 됐다. 2단일 때는 1080×1920 원본이
     * 들어와도 406×720 이 상한이라 해상도를 버렸다. 대역폭이 모자라면 ABR 이
     * 720p·480p 로 내려가면 된다 — 그러라고 사다리를 둔다.
     *
     * <p><b>원본보다 큰 단은 실행 시점에 걸러진다</b>({@code HlsPackager.ladderFor}).
     * 여기 있다고 항상 만들어지는 것이 아니다.
     */
    public static final java.util.List<Rendition> LADDER = java.util.List.of(
            new Rendition("480p", 480, 700),
            new Rendition("720p", 720, 1200),
            new Rendition("1080p", 1080, 2500));

    /** {@code -bufsize} 는 상한의 2배로 둔다 — 순간 변동을 흡수하되 평균은 상한에 묶인다. */
    public int bufsizeKbps() {
        return maxrateKbps * 2;
    }
}

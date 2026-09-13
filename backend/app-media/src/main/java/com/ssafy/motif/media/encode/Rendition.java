package com.ssafy.motif.media.encode;

/**
 * 화질 사다리의 한 단 (ADR-0010 D1).
 *
 * <p><b>세로 영상이므로 {@code height} 는 짧은 변이다.</b> 9:16 소스에서 720 은
 * 406×720 이 된다.
 *
 * @param tag         S3 키·재생목록 경로에 쓰는 이름 ({@code 720p})
 * @param height      짧은 변 픽셀
 * @param maxrateKbps 비트레이트 상한. CRF 만 쓰면 내용에 따라 원본 대비 15~69% 로
 *                    4.6배 편차가 난다 — ABR 이 재생목록의 {@code BANDWIDTH} 로
 *                    화질을 고르므로 사다리가 예측 가능해야 한다 (ADR-0010 D4)
 */
public record Rendition(String tag, int height, int maxrateKbps) {

    /** 채택 사다리 (ADR-0010 D1). 낮은 화질부터 — 마스터 재생목록 순서와 같다. */
    public static final java.util.List<Rendition> LADDER = java.util.List.of(
            new Rendition("480p", 480, 700),
            new Rendition("720p", 720, 1200));

    /** {@code -bufsize} 는 상한의 2배로 둔다 — 순간 변동을 흡수하되 평균은 상한에 묶인다. */
    public int bufsizeKbps() {
        return maxrateKbps * 2;
    }
}

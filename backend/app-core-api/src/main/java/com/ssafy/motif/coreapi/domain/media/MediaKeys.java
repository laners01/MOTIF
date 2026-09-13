package com.ssafy.motif.coreapi.domain.media;

/**
 * 산출물 S3 키 규약 — <b>한 곳에서만 정한다</b>.
 *
 * <p>app-media 는 이 프리픽스 아래에 {@code master.m3u8}·{@code thumb.jpg}·화질별
 * 디렉터리를 깐다. DB 에는 마스터 재생목록 경로 하나만 저장하고 나머지는 이 규약으로
 * 찾으므로(ADR-0010 D4), 규약이 두 군데로 갈라지면 조각을 못 찾는다.
 *
 * <p><b>seq 가 아니라 uuid 를 쓴다.</b> 공개 CDN 경로에 순차 번호를 쓰면 열거가
 * 가능해 콘텐츠의 존재와 개수가 드러난다. 어드민 업로드 키가 이미 uuid 기반이고
 * ({@code challenges/{uuid}.mp4} — 어드민 명세 §1.4), 합성 잡은
 * {@code composite_jobs.job_uuid} 를 갖고 있다. 원본과 산출물이 같은 uuid 로 묶인다.
 */
public final class MediaKeys {

    private MediaKeys() {
    }

    /**
     * 합성 결과 — {@code results/{challenge|motif}/{jobUuid}}.
     *
     * <p>API 명세 §6.1·§8.2 의 {@code results/challenge/uuid/master.m3u8} 와 맞춘다.
     *
     * @param jobType {@code CHALLENGE} | {@code MOTIF} ({@code composite_jobs.job_type})
     * @param jobUuid {@code composite_jobs.job_uuid}
     */
    public static String forCompositeResult(String jobType, String jobUuid) {
        String kind = switch (jobType) {
            case "CHALLENGE" -> "challenge";
            case "MOTIF" -> "motif";
            default -> throw new IllegalArgumentException("알 수 없는 jobType: " + jobType);
        };
        return "results/" + kind + "/" + require(jobUuid, "jobUuid");
    }

    /**
     * 어드민이 올린 챌린지 원본 — {@code challenges/{uuid}}.
     *
     * <p>원본 업로드 키가 {@code challenges/{uuid}.mp4} 이므로(어드민 명세 §1.4)
     * 같은 uuid 아래에 산출물이 깔린다. API 명세 §7.1 예시와 일치한다.
     *
     * @param videoUuid 업로드 키의 uuid
     */
    public static String forChallengeOrigin(String videoUuid) {
        return "challenges/" + require(videoUuid, "videoUuid");
    }

    /**
     * 에셋 썸네일 — {@code assets/{uuid}/thumb.jpg} (어드민 명세 §1.4 3항).
     *
     * <p>등록 시점에는 <b>아직 없는 파일</b>을 가리킨다. 규약으로 계산해 넣고 파일은
     * app-media 가 만들며, 그때까지 썸네일 URL 은 404 다(§8 #19). 에셋 미리보기는
     * HLS 로 가공하지 않으므로(§8 #17 — MP4 유지) 산출물은 이 썸네일 하나뿐이다.
     *
     * @param videoKey 업로드 키 {@code assets/{uuid}.mp4}
     */
    public static String forAssetThumbnail(String videoKey) {
        String key = require(videoKey, "videoKey");
        int dot = key.lastIndexOf('.');
        return (dot < 0 ? key : key.substring(0, dot)) + "/thumb.jpg";
    }

    private static String require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " 가 비어 있다");
        }
        return v;
    }
}

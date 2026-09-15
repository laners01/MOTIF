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
 *
 * <p>같은 이유로 <b>경로에 {@code user_seq} 를 넣지 않는다.</b> signed URL 은 URL 단위로
 * 서명하므로 소유자를 경로에 새길 필요가 없고(인가는 서명을 내주기 전에 core-api 가
 * 한다), 넣으면 순차 정수가 CDN 네임스페이스에 드러난다. 사용자별 정리가 필요하면
 * {@code composite_jobs} 에 {@code user_seq} 와 {@code job_uuid} 가 함께 있으므로
 * DB 로 프리픽스를 뽑는다.
 *
 * <p>── 최상위 프리픽스가 곧 보호 등급이다 (2026-09-15) ──────────────────────
 *
 * <pre>
 * private/{challenge|motif}/{jobUuid}/   서명 필요 — CloudFront behavior `private/*`
 * public/challenge/{jobUuid}/            서명 없음
 * challenges/{uuid}/                     서명 없음 — 어드민이 올린 챌린지 원본
 * assets/{assetUuid}/                    서명 없음 — 에셋 미리보기·썸네일
 * </pre>
 *
 * CloudFront 의 "Restrict viewer access" 는 <b>캐시 동작 단위</b>이고 캐시 동작은
 * path pattern 으로 고른다. 그래서 보호 여부가 경로 최상단에 드러나 있어야 동작
 * 하나로 가른다 — 같은 프리픽스에 섞으면 behavior 로 나눌 방법이 없다.
 */
public final class MediaKeys {

    private MediaKeys() {
    }

    /**
     * 합성 결과 — {@code private/{challenge|motif}/{jobUuid}}.
     *
     * <p><b>패키징 시점에는 언제나 보호 등급이다.</b> 참여 영상의 {@code visibility} 는
     * 게시(§8.1)에서 정해지는데 가공은 그보다 먼저 끝나 있어야 한다 — 게시 전 프리뷰도
     * 재생 대상이기 때문이다(§1.7 "게시·저장 전 합성 결과 프리뷰"). 그래서 여기서는
     * 공개 여부를 묻지 않고, PUBLIC 으로 게시될 때 {@link #forPublishedChallenge} 로
     * 복사한다.
     *
     * <p>모티프 결과물은 공개 등급이 없어 평생 이 프리픽스에 남는다.
     *
     * @param jobType {@code CHALLENGE} | {@code MOTIF} ({@code composite_jobs.job_type})
     * @param jobUuid {@code composite_jobs.job_uuid}
     */
    public static String forCompositeResult(String jobType, String jobUuid) {
        return "private/" + kind(jobType) + "/" + require(jobUuid, "jobUuid");
    }

    /**
     * PUBLIC 으로 게시된 참여 영상 — {@code public/challenge/{jobUuid}}.
     *
     * <p>{@link #forCompositeResult} 가 만든 트리를 여기로 <b>복사</b>한다. 옮기지 않는
     * 이유는 게시 후에도 본인은 보호 경로로 계속 볼 수 있어야 하고, 무엇보다
     * {@code composite_jobs.result_video_key} 가 가리키는 값을 바꾸는 순간 재생 중인
     * 클라이언트가 404 를 만나기 때문이다.
     *
     * <p><b>반대 방향(PUBLIC → PRIVATE)은 비싸다.</b> 산출물에 1년 {@code immutable} 이
     * 붙어 있어 S3 에서 지워도 엣지가 계속 서빙한다 — CloudFront 무효화가 따로 필요하다.
     * {@code visibility} 를 단방향으로 제한하는 근거다(어드민·API 명세 §8.2 참조).
     *
     * @param jobUuid {@code composite_jobs.job_uuid} — {@code CHALLENGE} 잡만 해당한다
     */
    public static String forPublishedChallenge(String jobUuid) {
        return "public/challenge/" + require(jobUuid, "jobUuid");
    }

    /**
     * 어드민이 올린 챌린지 원본 — {@code challenges/{uuid}}.
     *
     * <p>원본 업로드 키가 {@code challenges/{uuid}.mp4} 이므로(어드민 명세 §1.4)
     * 같은 uuid 아래에 산출물이 깔린다. API 명세 §7.1 예시와 일치한다.
     *
     * <p>전부 공개 등급이라 {@code public/} 아래로 옮기지 않는다 — 옮기면 이미 배포된
     * 객체의 CDN 무효화와 DB 갱신이 따라붙는데 얻는 것이 없다.
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

    /** {@code composite_jobs.job_type} → 경로 세그먼트. */
    private static String kind(String jobType) {
        return switch (jobType) {
            case "CHALLENGE" -> "challenge";
            case "MOTIF" -> "motif";
            default -> throw new IllegalArgumentException("알 수 없는 jobType: " + jobType);
        };
    }

    private static String require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " 가 비어 있다");
        }
        return v;
    }
}

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
 * challenges/{uuid}/                     서명 없음 — 어드민이 올린 챌린지 원본
 * assets/{assetUuid}/                    서명 없음 — 에셋 미리보기·썸네일
 * raw/{jobUuid}.mp4                      읽기 경로 없음 — 사용자 촬영 원본
 * results/{challenge|motif}/{uuid}.mp4  읽기 경로 없음 — AI 산출물(가공 전)
 * </pre>
 *
 * CloudFront 의 "Restrict viewer access" 는 <b>캐시 동작 단위</b>이고 캐시 동작은
 * path pattern 으로 고른다. 그래서 보호 여부가 경로 최상단에 드러나 있어야 동작
 * 하나로 가른다 — 같은 프리픽스에 섞으면 behavior 로 나눌 방법이 없다.
 *
 * <p><b>공개 사본 트리({@code public/challenge/})는 없다</b> (ADR-0013). 합성 결과는
 * 게시 여부와 무관하게 {@code private/} 한 곳에 머물고, {@code visibility} 는 앱 안에서
 * 누가 볼 자격이 있는가만 정한다. 사본을 두던 시절에는 복사기·삭제기·CloudFront
 * 무효화가 필요했는데, 서명 파라미터가 캐시 키에 들어가지 않아 "서명 없이 내보내
 * 캐시를 극대화한다" 는 이점이 애초에 없었다.
 */
public final class MediaKeys {

    /**
     * 프리픽스 아래 마스터 재생목록의 파일명.
     *
     * <p>{@code app-media} 의 {@code HlsPackager.MASTER} 와 같은 값이다 — 모듈이 갈려
     * 상수를 공유하지 못한다. 한쪽을 바꾸면 산출물을 못 찾는다.
     */
    public static final String MASTER_PLAYLIST = "master.m3u8";

    /** 보호 등급 프리픽스. CloudFront 캐시 동작 {@code private/*} 과 같은 값이다. */
    private static final String PROTECTED_PREFIX = "private/";

    private MediaKeys() {
    }

    /**
     * 사용자 촬영 원본 — {@code raw/{jobUuid}.mp4} (API 명세 §13.1).
     *
     * <p><b>프리픽스 아래 트리가 아니라 파일 하나다.</b> 가공 대상이지 재생 대상이 아니라
     * HLS 로 풀리지 않는다 — 워커가 읽어 합성에 쓰고, 산출물은
     * {@link #forCompositeResult} 아래로 간다.
     *
     * <p><b>{@code private/} 가 아닌데도 새지 않는다.</b> CloudFront 에는 {@code raw/*}
     * 동작이 없어 CDN 으로 열리는 주소 자체가 없고, 쓰기는 이 키로 발급한 presigned PUT
     * (600초)뿐이다. {@code private/} 아래에 두면 "보호 등급 = 재생 대상" 이라는 규약이
     * 흐려진다 — 그 프리픽스의 것은 전부 서명 발급 대상인데 원본은 아무도 재생하지 않는다.
     *
     * <p>수명주기로 지워진다 — 재시도(§13.4)가 410 {@code RAW_VIDEO_EXPIRED} 를 주는 것이
     * 그 때문이다.
     *
     * @param jobUuid {@code composite_jobs.job_uuid}
     */
    public static String forRawUpload(String jobUuid) {
        return "raw/" + require(jobUuid, "jobUuid") + ".mp4";
    }

    /**
     * 이 키가 보호 등급인가 — <b>등급 판정은 여기서만 한다</b>.
     *
     * <p><b>{@code visibility} 로 판정하지 않는 이유.</b> 둘은 다른 층이다 —
     * {@code visibility} 는 <b>앱 안에서 누가 볼 자격이 있는가</b>이고, 등급은 <b>그
     * 자격을 CDN 에 어떻게 전달하는가</b>다(ADR-0013). 참여 영상은 {@code visibility}
     * 와 무관하게 전부 {@code private/} 에 있으므로, {@code visibility} 로 가르면
     * {@code PUBLIC} 영상에 무서명 URL 이 나가 403 이 된다.
     *
     * <p>키 하나만 보면 언제나 맞는다 — 프리픽스가 곧 등급이라는 규약이 S3 ·
     * CloudFront 동작 · 이 판정에서 같은 것을 가리키기 때문이다.
     */
    public static boolean isProtected(String key) {
        return key != null && key.startsWith(PROTECTED_PREFIX);
    }

    /**
     * 합성 결과 — {@code private/{challenge|motif}/{jobUuid}}.
     *
     * <p><b>여기가 합성 결과의 유일한 자리다.</b> 게시되든 안 되든, PUBLIC 이든
     * PRIVATE 이든 옮기지 않는다(ADR-0013). 가공은 게시(§8.8)보다 먼저 끝나 있어야
     * 하는데 — 게시 전 프리뷰도 재생 대상이다(§1.7 "게시·저장 전 합성 결과 프리뷰") —
     * 그 시점에 아직 모르는 {@code visibility} 로 자리를 가를 이유가 없다.
     *
     * @param jobType {@code CHALLENGE} | {@code MOTIF} ({@code composite_jobs.job_type})
     * @param jobUuid {@code composite_jobs.job_uuid}
     */
    public static String forCompositeResult(String jobType, String jobUuid) {
        return "private/" + kind(jobType) + "/" + require(jobUuid, "jobUuid");
    }

    /**
     * AI 워커가 결과를 올릴 자리 — {@code results/{challenge|motif}/{jobUuid}.mp4}
     * (AI 서버 명세 §3.2 {@code output_video_key}).
     *
     * <p><b>{@link #forCompositeResult} 와 다른 것이다.</b> 저쪽은 app-media 가 HLS 로
     * 푼 뒤의 <b>프리픽스</b>({@code private/…})이고, 여기는 AI 가 PUT 할 <b>MP4 파일</b>
     * 하나다. 순서가 {@code results/…mp4} → {@code private/…/master.m3u8} 라서 둘 다 있다.
     *
     * <p>재시도(§13.4)는 <b>같은 키를 덮어쓴다</b> — 프리픽스가 {@code jobUuid} 로 멱등해
     * 시도 횟수가 경로에 섞이지 않는다. 덕분에 늦게 도착한 콜백이 엉뚱한 파일을 가리키는
     * 일이 없다.
     *
     * @param jobType {@code CHALLENGE} | {@code MOTIF} ({@code composite_jobs.job_type})
     * @param jobUuid {@code composite_jobs.job_uuid}
     */
    public static String forWorkerOutput(String jobType, String jobUuid) {
        return "results/" + kind(jobType) + "/" + require(jobUuid, "jobUuid") + ".mp4";
    }

    /**
     * 저장된 {@code video_key} → <b>원본 MP4 키</b> (AI 서버 명세 §3.2 {@code base_video_key}).
     *
     * <p><b>가공이 끝난 챌린지·에셋의 {@code video_key} 는 MP4 가 아니다.</b> app-media 가
     * {@code …/master.m3u8} 로 덮어쓰기 때문이다(ADR-0010 D4). 그대로 AI 에 보내면
     * <b>재생목록 텍스트를 영상으로 열려다</b> 실패한다(작업명세 §7 함정 3).
     *
     * <p>업로드 키 규약이 {@code {프리픽스}.mp4} 와 {@code {프리픽스}/master.m3u8} 로 짝을
     * 이루므로({@link #forAssetPreview}·{@link #forChallengeOrigin}) 프리픽스에
     * {@code .mp4} 를 붙이면 원본이다.
     *
     * <p><b>가공 전 값과 편집본 키는 그대로 돌려준다</b> — 이미 MP4 다. 되돌릴 것이 없는
     * 키를 건드리지 않는 덕에 호출부가 "이 값이 가공을 거쳤나" 를 묻지 않아도 된다.
     */
    public static String sourceMp4Of(String key) {
        String value = require(key, "key");
        return value.endsWith("/" + MASTER_PLAYLIST) ? prefixOf(value) + ".mp4" : value;
    }

    /**
     * 어드민이 올린 챌린지 원본 — {@code challenges/{uuid}}.
     *
     * <p>원본 업로드 키가 {@code challenges/{uuid}.mp4} 이므로(어드민 명세 §1.4)
     * 같은 uuid 아래에 산출물이 깔린다. API 명세 §7.1 예시와 일치한다.
     *
     * <p><b>전부 공개 등급이라 서명하지 않는다.</b> 소유자 개념이 없고 앱 전체에 똑같이
     * 보이는 카탈로그라 등급을 가를 것이 없다. 사용자 생성 콘텐츠(참여 영상·모티프
     * 결과)만 {@code private/} 로 간다(ADR-0013).
     *
     * @param videoUuid 업로드 키의 uuid
     */
    public static String forChallengeOrigin(String videoUuid) {
        return "challenges/" + require(videoUuid, "videoUuid");
    }

    /**
     * 에셋 미리보기 산출물 프리픽스 — {@code assets/{uuid}} (어드민 명세 §1.4 3항).
     *
     * <p>업로드 키가 {@code assets/{uuid}.mp4} 이므로 확장자를 떼면 프리픽스다.
     * {@link #forChallengeOrigin} 과 같은 모양이고 이유도 같다 — 원본과 산출물이 같은
     * uuid 로 묶인다.
     *
     * <p><b>에셋도 HLS 다</b>(2026-09-17, API 명세 §1.7). 숍 화면에서 재생되므로 앱이
     * 재생하는 다른 영상과 같은 규약을 쓴다. 가로 영상이지만 사다리 값이 짧은 변이라
     * 챌린지 원본과 같은 파라미터로 간다.
     *
     * <p>전부 공개 등급이라 {@code private/} 아래로 가지 않는다.
     *
     * @param videoKey 업로드 키 {@code assets/{uuid}.mp4}
     */
    public static String forAssetPreview(String videoKey) {
        String key = require(videoKey, "videoKey");
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    /**
     * 에셋 썸네일 — {@code assets/{uuid}/thumb.jpg}.
     *
     * <p>등록 시점에는 <b>아직 없는 파일</b>을 가리킨다. 규약으로 계산해 넣고 파일은
     * app-media 가 만들며, 그때까지 썸네일 URL 은 404 다(§8 #19).
     *
     * <p><b>워커가 쓰는 값과 같아야 한다</b> — 워커는 {@code outputPrefix + "/thumb.jpg"}
     * 를 기록하므로, 여기서도 {@link #forAssetPreview} 프리픽스 위에서 만든다. 두 곳이
     * 갈리면 등록 직후와 가공 후의 썸네일 키가 달라진다.
     *
     * @param videoKey 업로드 키 {@code assets/{uuid}.mp4}
     */
    public static String forAssetThumbnail(String videoKey) {
        return forAssetPreview(videoKey) + "/thumb.jpg";
    }

    /**
     * 마스터 재생목록 키 → 산출물 프리픽스 ({@code …/master.m3u8} 를 떼어낸다).
     *
     * <p>DB 에는 마스터 재생목록 경로 하나만 저장하는데(ADR-0010 D4), 화질별 재생목록과
     * 썸네일을 찾으려면 그 위 디렉터리가 필요하다. 저장된 값에서 거꾸로 구하는 것이
     * {@link #forCompositeResult} 로 다시 계산하는 것보다 안전하다 — 워커가 실제로 쓴
     * 자리를 그대로 따라가므로, 규약이 나중에 바뀌어도 이미 만들어진 산출물을 못 찾는
     * 일이 없다.
     *
     * @param masterKey {@code composite_jobs.result_video_key} 같은 {@code …/master.m3u8}
     * @throws IllegalArgumentException 마스터 재생목록 키가 아닐 때. 조용히 통과시키면
     *                                  엉뚱한 프리픽스에 서명이 나간다
     */
    public static String prefixOf(String masterKey) {
        String key = require(masterKey, "masterKey");
        String suffix = "/" + MASTER_PLAYLIST;
        if (!key.endsWith(suffix)) {
            throw new IllegalArgumentException("마스터 재생목록 키가 아니다: " + key);
        }
        return key.substring(0, key.length() - suffix.length());
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

package com.ssafy.motif.coreapi.admin.media;

/**
 * presign 요청의 {@code kind} → S3 프리픽스 (어드민 명세 §1.4 표) — S15P21D104-588.
 *
 * <p><b>오브젝트 키는 전부 UUID 기반이고 원본 파일명을 쓰지 않는다.</b> 공개 CDN 경로에
 * 순차 번호나 사용자 파일명을 쓰면 열거·정보 노출이 된다({@code MediaKeys} 와 같은 근거).
 *
 * <p>확장자는 Content-Type 이 아니라 <b>표가 고정</b>한다. {@code challenges/{uuid}.mp4}
 * 규약 위에 산출물 프리픽스 {@code challenges/{uuid}/} 가 얹히기 때문에(ADR-0010 D4),
 * 키에서 확장자를 떼면 산출물 경로가 나와야 한다. 실제 미디어 타입은 PUT 시 서명된
 * {@code Content-Type} 으로 S3 에 기록되므로 확장자가 고정이어도 서빙에 문제가 없다.
 */
public enum AdminMediaKind {

    /** {@code challenges/{uuid}.mp4} — {@code raw/} 는 사용자 원본 전용이라 분리한다. */
    CHALLENGE_VIDEO(Category.VIDEO, "challenges", ".mp4", false),

    /**
     * {@code private/edited/{challengeSeq}/{uuid}.mp4} — 기본 경로는 AI팀 직접 업로드,
     * presign 은 대체 경로.
     *
     * <p><b>{@code private/} 아래인 것이 의도다</b>(0020). 앱이 §7.6 으로 편집본 영상을
     * 직접 받게 되면서 서명이 필요해졌는데, 서명 여부는 {@link com.ssafy.motif.coreapi.domain.media.MediaKeys#isProtected}
     * 와 CloudFront 캐시 동작({@code private/*})이 <b>프리픽스로</b> 가른다. 여기를
     * {@code edited/} 로 되돌리면 서버는 서명을 붙이는데 CloudFront 가 검증하지 않아
     * 무서명 요청이 그대로 통과한다 — 예외도 로그도 없이 보호가 풀린다.
     */
    EDITED_VIDEO(Category.VIDEO, "private/edited", ".mp4", true),

    /** {@code private/edited/{challengeSeq}/{uuid}.jpg} — 편집본 첫 장면. */
    EDITED_SCENE(Category.IMAGE, "private/edited", ".jpg", true),

    /** {@code private/edited/{challengeSeq}/{uuid}.jpg} — 슬롯 선택 화면 이미지(S15P21D104-618). 첫 장면과 같은 자리·다른 용도라 로그에서 구분되게 kind 만 나눈다. */
    EDITED_PREVIEW(Category.IMAGE, "private/edited", ".jpg", true),

    /** {@code assets/{uuid}.mp4} — 모티프 에셋 미리보기. */
    ASSET_VIDEO(Category.VIDEO, "assets", ".mp4", false),

    /** {@code profiles/{uuid}.jpg} — 사용자 프로필과 프리픽스를 공유한다. */
    ARTIST_PROFILE(Category.IMAGE, "profiles", ".jpg", false),

    /** {@code artists/{uuid}.jpg} — 아티스트 모티프 배경(API §11.4 {@code bannerImageUrl}). */
    ARTIST_MOTIF_BG(Category.IMAGE, "artists", ".jpg", false),

    /** {@code notices/{uuid}.jpg} */
    NOTICE_IMAGE(Category.IMAGE, "notices", ".jpg", false),

    /** {@code banners/{uuid}.jpg} — 숍 배너(DB §40). */
    BANNER_IMAGE(Category.IMAGE, "banners", ".jpg", false);

    /** 허용 Content-Type·크기 상한·만료가 갈리는 단위 (§1.4). */
    public enum Category { VIDEO, IMAGE }

    /** 아직 챌린지가 없을 때 {@code EDITED_*} 가 쓰는 자리 (§3.2 기본안). */
    private static final String PENDING = "pending";

    private final Category category;
    private final String prefix;
    private final String extension;
    private final boolean challengeScoped;

    AdminMediaKind(Category category, String prefix, String extension, boolean challengeScoped) {
        this.category = category;
        this.prefix = prefix;
        this.extension = extension;
        this.challengeScoped = challengeScoped;
    }

    public Category category() {
        return category;
    }

    /**
     * @param uuid         서버가 만든 오브젝트 uuid
     * @param challengeSeq {@code EDITED_*} 에서만 쓴다. 신규 등록 중이라 아직 없으면
     *                     {@code null} — {@code edited/pending/{uuid}} 로 발급하고
     *                     등록 시 그 경로를 그대로 쓴다(§3.2)
     */
    public String objectKey(String uuid, Integer challengeSeq) {
        if (!challengeScoped) {
            return prefix + "/" + uuid + extension;
        }
        return prefix + "/" + (challengeSeq == null ? PENDING : challengeSeq) + "/" + uuid + extension;
    }
}

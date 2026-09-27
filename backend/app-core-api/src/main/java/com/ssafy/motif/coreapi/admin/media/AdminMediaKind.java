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
     * {@code private/edited/{challengeSeq}/{challengeParticipantSeq}/{uuid}.mp4} — 기본
     * 경로는 AI팀 직접 업로드, presign 은 대체 경로.
     *
     * <p><b>{@code private/} 아래인 것이 의도다</b>(0020). 앱이 §7.6 으로 편집본 영상을
     * 직접 받게 되면서 서명이 필요해졌는데, 서명 여부는 {@link com.ssafy.motif.coreapi.domain.media.MediaKeys#isProtected}
     * 와 CloudFront 캐시 동작({@code private/*})이 <b>프리픽스로</b> 가른다. 여기를
     * {@code edited/} 로 되돌리면 서버는 서명을 붙이는데 CloudFront 가 검증하지 않아
     * 무서명 요청이 그대로 통과한다 — 예외도 로그도 없이 보호가 풀린다.
     */
    EDITED_VIDEO(Category.VIDEO, Prefixes.EDITED, ".mp4", true),

    /** {@code private/edited/{challengeSeq}/{challengeParticipantSeq}/{uuid}.jpg} — 편집본 첫 장면. */
    EDITED_SCENE(Category.IMAGE, Prefixes.EDITED, ".jpg", true),

    /** {@code private/edited/{challengeSeq}/{challengeParticipantSeq}/{uuid}.jpg} — 슬롯 선택 화면 이미지(S15P21D104-618). 첫 장면과 같은 자리·다른 용도라 로그에서 구분되게 kind 만 나눈다. */
    EDITED_PREVIEW(Category.IMAGE, Prefixes.EDITED, ".jpg", true),

    /** {@code assets/{uuid}.mp4} — 모티프 에셋 미리보기. */
    ASSET_VIDEO(Category.VIDEO, "assets", ".mp4", false),

    /**
     * {@code assets/{uuid}.jpg} — 모티프 에셋 썸네일을 직접 올릴 때. 보통은 app-media 가 영상에서
     * 뽑으므로 쓰지 않는다. 영상을 다른 에셋과 공유하는 시연 데이터처럼 썸네일만 따로 둘 때 쓴다.
     */
    ASSET_THUMBNAIL(Category.IMAGE, "assets", ".jpg", false),

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

    /**
     * 열거 상수가 쓰는 프리픽스 — <b>enum 은 자기 static 필드를 상수 인자로 못 쓴다</b>
     * (상수가 static 초기화보다 먼저 만들어진다). 별도 클래스로 빼면 참조할 수 있다.
     */
    private static final class Prefixes {
        static final String EDITED = "private/edited";

        private Prefixes() {
        }
    }

    /**
     * 그 슬롯의 편집본이 놓이는 프리픽스 — {@code private/edited/{challengeSeq}/{slotSeq}/}.
     *
     * <p>{@link #objectKey} 와 <b>같은 규약을 쓴다</b>. §3.7.6 이 넘어온 키가 정말 그 슬롯
     * 자리인지 대조하는 데 쓰는데, 조립과 검사가 각자 문자열을 만들면 한쪽만 고쳐졌을 때
     * 검사가 통과하면서 키는 엉뚱한 자리를 가리킨다.
     */
    public static String editedPrefix(Integer challengeSeq, Integer challengeParticipantSeq) {
        return Prefixes.EDITED + "/" + challengeSeq + "/" + challengeParticipantSeq + "/";
    }

    private final Category category;
    private final String prefix;
    private final String extension;
    private final boolean slotScoped;

    AdminMediaKind(Category category, String prefix, String extension, boolean slotScoped) {
        this.category = category;
        this.prefix = prefix;
        this.extension = extension;
        this.slotScoped = slotScoped;
    }

    public Category category() {
        return category;
    }

    /** 키가 슬롯에 매여 있는가 — {@code EDITED_*} 만 {@code true} 다. */
    public boolean isSlotScoped() {
        return slotScoped;
    }

    /**
     * 오브젝트 키를 만든다.
     *
     * <p><b>{@code EDITED_*} 는 슬롯까지 내려간다</b> —
     * {@code private/edited/{challengeSeq}/{challengeParticipantSeq}/{uuid}.{ext}}.
     * 챌린지 하나에 슬롯이 여럿이라 프리픽스를 챌린지에서 끊으면 어느 슬롯 것인지
     * 경로로 알 수 없고, 슬롯을 지웠을 때 딸린 파일을 프리픽스로 걷어낼 수도 없다.
     *
     * <p><b>파일명은 uuid 로 남긴다.</b> 첫 장면과 선택 화면이 같은 자리·같은 확장자라
     * 슬롯 seq 를 파일명으로 쓰면 둘이 겹친다. uuid 면 영상을 교체해도 새 오브젝트가
     * 쌓여 이전 파일을 덮지 않는다 — CloudFront 가 옛 영상을 계속 서빙할 일이 없다.
     *
     * <p>{@code pending} 자리표시자는 없다. 편집본은 챌린지·슬롯이 이미 있는 상태에서만
     * 올라오므로(등록 §3.7.2 → 편집본 §3.7.6) 두 seq 가 언제나 정해져 있다.
     *
     * @param uuid                    서버가 만든 오브젝트 uuid
     * @param challengeSeq            {@code EDITED_*} 필수. 그 밖에는 쓰지 않는다
     * @param challengeParticipantSeq {@code EDITED_*} 필수. 그 밖에는 쓰지 않는다
     * @throws IllegalArgumentException {@code EDITED_*} 인데 seq 가 비었을 때. 호출부가
     *                                  먼저 400 으로 거르므로(AdminMediaService) 여기
     *                                  걸리면 거르지 않은 경로가 생긴 것이다 — {@code null}
     *                                  이 키 문자열에 섞여 들어가는 것보다 낫다
     */
    public String objectKey(String uuid, Integer challengeSeq, Integer challengeParticipantSeq) {
        if (!slotScoped) {
            return prefix + "/" + uuid + extension;
        }
        if (challengeSeq == null || challengeParticipantSeq == null) {
            throw new IllegalArgumentException(
                    "%s 는 challengeSeq·challengeParticipantSeq 가 필요하다".formatted(this));
        }
        return editedPrefix(challengeSeq, challengeParticipantSeq) + uuid + extension;
    }
}

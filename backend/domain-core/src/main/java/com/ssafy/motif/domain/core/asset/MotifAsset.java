package com.ssafy.motif.domain.core.asset;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모티프 에셋 (DB SPEC v0.15 §30, changeSet 0009-01).
 *
 * <p><b>{@code is_deleted} 가 없다.</b> 과거 주문·구매·합성 잡이 참조하므로 물리 삭제도
 * 소프트 삭제도 하지 않고 종료 상태를 {@code is_active} 로 표현한다(§30).
 *
 * <p>{@code thumbnail_key} 는 등록 시점에 <b>아직 없는 파일</b>을 가리킨다 — 규약으로
 * 계산해 넣고 파일은 app-media 가 만든다(어드민 명세 §1.4 3항, §8 #19). 미리보기 영상은
 * HLS 로 가공하지 않고 <b>MP4 그대로</b> 서빙한다(§8 #17 — 가로 ≤20초라 ABR 이득이 작다).
 */
@Entity
@Table(name = "motif_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotifAsset extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_seq")
    private Integer assetSeq;

    @Column(name = "artist_seq", nullable = false)
    private Integer artistSeq;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "video_key", nullable = false)
    private String videoKey;

    @Column(name = "thumbnail_key", nullable = false)
    private String thumbnailKey;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 10)
    private AssetType assetType;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static MotifAsset register(Integer artistSeq, String title, String description,
                                      String videoKey, String thumbnailKey, int durationMs,
                                      AssetType assetType) {
        MotifAsset asset = new MotifAsset();
        asset.artistSeq = artistSeq;
        asset.title = title;
        asset.description = description;
        asset.videoKey = videoKey;
        asset.thumbnailKey = thumbnailKey;
        asset.durationMs = durationMs;
        asset.assetType = assetType;
        asset.active = true;
        return asset;
    }

    /**
     * 수정 (어드민 명세 §3.8.4) — {@code null} 은 "두라" 다.
     *
     * <p>빈 문자열과 {@code null} 을 구분한다 — {@code description} 은 nullable 이라
     * "소개를 지운다"(빈 문자열)와 "건드리지 않는다"({@code null})가 다른 요청이다.
     */
    public void update(String title, String description, AssetType assetType) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (assetType != null) {
            this.assetType = assetType;
        }
    }

    /**
     * 영상 교체 (§3.8.4).
     *
     * <p>썸네일 키도 같이 바꾼다 — 둘은 한 규약으로 묶여 있다({@code MediaKeys}).
     * 따로 놓으면 새 영상에 옛 썸네일이 붙은 채 남는다.
     */
    public void replaceVideo(String videoKey, String thumbnailKey, int durationMs) {
        this.videoKey = videoKey;
        this.thumbnailKey = thumbnailKey;
        this.durationMs = durationMs;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }
}

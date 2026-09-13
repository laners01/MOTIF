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

    public void changeActive(boolean active) {
        this.active = active;
    }
}

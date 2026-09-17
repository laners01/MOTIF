package com.ssafy.motif.coreapi.admin.asset;

import com.ssafy.motif.coreapi.admin.asset.dto.AssetUpdateRequest;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 에셋 부분 수정 규칙 (어드민 명세 §3.8.4).
 *
 * <p>순수 계산이라 DB 없이 돈다 — CI 에서 매번 돈다({@code local-db} 태그 제외).
 */
class AssetUpdateRulesTest {

    private static MotifAsset asset() {
        return MotifAsset.register(3, "원래 제목", "원래 소개",
                "assets/old.mp4", "assets/old/thumb.jpg", 9800, AssetType.NORMAL);
    }

    private static AssetUpdateRequest request(String title, String description, AssetType type,
                                              String videoKey, Long durationMs,
                                              Integer width, Integer height) {
        return new AssetUpdateRequest(title, description, type, videoKey, durationMs, width, height,
                null);
    }

    @Test
    @DisplayName("null 은 건드리지 않는다 — 부분 수정이라 보낸 필드만 반영한다")
    void null_은_유지() {
        MotifAsset asset = asset();

        asset.update(null, null, AssetType.PREMIUM);

        assertThat(asset.getTitle()).isEqualTo("원래 제목");
        assertThat(asset.getDescription()).isEqualTo("원래 소개");
        assertThat(asset.getAssetType()).isEqualTo(AssetType.PREMIUM);
    }

    @Test
    @DisplayName("빈 문자열은 null 과 다르다 — '소개를 지운다'와 '건드리지 않는다'가 다른 요청이다")
    void 빈_문자열은_지우는_것() {
        MotifAsset asset = asset();

        asset.update(null, "", null);

        assertThat(asset.getDescription()).isEmpty();
    }

    @Test
    @DisplayName("영상 교체는 썸네일 키도 함께 바꾼다 — 따로 두면 새 영상에 옛 썸네일이 붙는다")
    void 영상_교체는_썸네일도() {
        MotifAsset asset = asset();

        asset.replaceVideo("assets/new.mp4", "assets/new/thumb.jpg", 12000);

        assertThat(asset.getVideoKey()).isEqualTo("assets/new.mp4");
        assertThat(asset.getThumbnailKey()).isEqualTo("assets/new/thumb.jpg");
        assertThat(asset.getDurationMs()).isEqualTo(12000);
    }

    @Test
    @DisplayName("videoKey 만 오면 거부한다 — 서버는 영상을 열어 볼 수 없어 대조할 값이 없다")
    void 영상만_오면_메타_누락() {
        assertThat(request(null, null, null, "assets/new.mp4", null, null, null).videoMetaMissing())
                .isTrue();
        assertThat(request(null, null, null, "assets/new.mp4", 12000L, 1920, null).videoMetaMissing())
                .isTrue();
    }

    @Test
    @DisplayName("영상을 안 바꾸면 메타가 없어도 정상이다 — 제목만 고치는 요청이 막히면 안 된다")
    void 영상_없으면_메타_불필요() {
        AssetUpdateRequest request = request("새 제목", null, null, null, null, null, null);

        assertThat(request.hasVideo()).isFalse();
        assertThat(request.videoMetaMissing()).isFalse();
    }

    @Test
    @DisplayName("빈 videoKey 는 교체 요청이 아니다 — 폼이 빈 칸을 그대로 보내도 통과해야 한다")
    void 빈_videoKey() {
        assertThat(request(null, null, null, "  ", null, null, null).hasVideo()).isFalse();
    }
}

package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.domain.asset.dto.ShopSectionItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.artist.SubscribedArtistRow;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository.AssetShopRow;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import com.ssafy.motif.domain.core.pass.MotifPassRepository.ActivePassProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 숍 전량 조회 (API 명세 §11.3).
 *
 * <p><b>조회는 세 번으로 고정이다</b> — 구독 · 에셋 · 패스. 아티스트 수와 무관하다.
 * 이 API 가 되살아난 이유가 "구독 수만큼 §11.1 을 부르는" 왕복을 없애는 것이라,
 * 안에서 N+1 을 만들면 목적이 사라진다.
 *
 * <p><b>섹션 순서의 출처는 구독 조회 하나다.</b> 에셋 쿼리에 {@code subscriptions} 를
 * 조인해 순서까지 맡기지 않는다 — 순서가 두 곳에서 정해지면 §4.6·§4.7 과 어긋나는 날이
 * 온다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopQueryService {

    private final SubscriptionRepository subscriptionRepository;
    private final MotifAssetRepository motifAssetRepository;
    private final MotifPassRepository motifPassRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §11.3 숍 전량 — 구독 아티스트를 섹션으로, 각 섹션에 에셋 전량.
     *
     * <p>실패 분기가 없다. 구독이 0건이면 빈 배열 200 이고, 에셋이 0건인 아티스트도
     * 섹션은 나온다({@code assets: []}).
     */
    public ListResponse<ShopSectionItem> shop(Integer userSeq) {
        List<SubscribedArtistRow> artists = subscriptionRepository.findSubscribed(userSeq);
        if (artists.isEmpty()) {
            // in () 는 Postgres 문법 오류다 — 뒤 두 조회를 부르지 않는다
            return ListResponse.of(List.of());
        }

        List<Integer> artistSeqs = artists.stream().map(SubscribedArtistRow::artistSeq).toList();
        Map<Integer, List<AssetShopRow>> assetsByArtist =
                motifAssetRepository.searchShop(userSeq, artistSeqs).stream()
                        // 쿼리가 이미 최신순이고 groupingBy 의 기본 리스트는 만난 순서를 지킨다
                        .collect(Collectors.groupingBy(AssetShopRow::getArtistSeq));
        Map<Integer, Instant> passExpiryByArtist =
                motifPassRepository.findActivePasses(userSeq, artistSeqs).stream()
                        .collect(Collectors.toMap(ActivePassProjection::getArtistSeq,
                                ActivePassProjection::getExpiresDttm));

        return ListResponse.of(artists.stream()
                .map(artist -> section(artist,
                        assetsByArtist.getOrDefault(artist.artistSeq(), List.of()),
                        passExpiryByArtist.get(artist.artistSeq())))
                .toList());
    }

    private ShopSectionItem section(SubscribedArtistRow artist, List<AssetShopRow> assets,
                                    Instant passExpiry) {
        boolean passActive = passExpiry != null;
        return new ShopSectionItem(
                new ShopSectionItem.Artist(
                        artist.artistSeq(),
                        artist.name(),
                        artist.groupName(),
                        mediaUrlResolver.resolveProfile(artist.profileImageKey())),
                passActive,
                passExpiry,
                assets.stream().map(row -> asset(row, passActive)).toList());
    }

    /**
     * {@code myAccess} 는 여기서 정해진다 — 행의 {@code owned} 와 <b>아티스트 단위</b>
     * 패스 보유 여부를 합친다.
     *
     * <p>패스 판정을 에셋마다 쿼리로 하지 않는 이유는 답이 아티스트당 하나이기 때문이다.
     * {@code PREMIUM} 이 패스로 열리지 않는 규칙은 {@link MyAccess#of} 가 갖는다.
     */
    private ShopSectionItem.Asset asset(AssetShopRow row, boolean passActive) {
        AssetType assetType = AssetType.valueOf(row.getAssetType());
        return new ShopSectionItem.Asset(
                row.getAssetSeq(),
                row.getTitle(),
                mediaUrlResolver.resolve(row.getThumbnailKey()),
                row.getDescription(),
                row.getDurationMs(),
                assetType,
                MyAccess.of(row.getOwned(), passActive, assetType));
    }
}

package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository.AssetCatalogRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팬 앱 에셋 조회 (API 명세 §11.1).
 *
 * <p><b>조회 한 번으로 끝난다.</b> 구독 · 노출 · 권한 판정이 모두
 * {@link MotifAssetRepository#searchCatalog} 안에 있어, 여기서 하는 일은 키를 URL 로
 * 바꾸고 {@code owned}·{@code passActive} 를 {@link MyAccess} 하나로 접는 것뿐이다.
 *
 * <p><b>URL 은 {@link MediaUrlResolver} 만 만든다.</b> 에셋 썸네일은 {@code assets/}
 * 프리픽스라 무서명이고 프로필도 공개지만, 호출부에서 등급을 직접 고르면 판정이 화면
 * 수만큼 늘어난다(§1.7 — 리졸버 javadoc).
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetQueryService {

    private final MotifAssetRepository motifAssetRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §11.1 에셋 목록 — {@code artistSeq} 가 {@code null} 이면 <b>내 구독 아티스트</b>의
     * 에셋이다.
     *
     * <p>실패 분기가 없다. 구독이 0건이든, 없는 {@code artistSeq} 를 받든 빈 목록 200 이다 —
     * 아티스트 존재를 여기서 검증하면 "구독 화면을 여는 데 아티스트 조회가 먼저 필요한"
     * 계약이 되고, 없는 번호로 존재 여부를 떠보는 경로도 열린다.
     */
    public PageResponse<AssetCatalogItem> assets(Integer userSeq, Integer artistSeq,
                                                 int page, int size) {
        return PageResponses.of(
                motifAssetRepository.searchCatalog(userSeq, artistSeq,
                        PageResponses.pageable(page, size)),
                this::item);
    }

    private AssetCatalogItem item(AssetCatalogRow row) {
        AssetType assetType = AssetType.valueOf(row.getAssetType());
        return new AssetCatalogItem(
                row.getAssetSeq(),
                new AssetCatalogItem.Artist(
                        row.getArtistSeq(),
                        row.getArtistName(),
                        row.getGroupName(),
                        mediaUrlResolver.resolve(row.getArtistProfileImageKey())),
                row.getTitle(),
                mediaUrlResolver.resolve(row.getThumbnailKey()),
                row.getDescription(),
                row.getDurationMs(),
                assetType,
                MyAccess.of(row.getOwned(), row.getPassActive(), assetType));
    }
}

package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetDetailResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository.AssetCatalogRow;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository.AssetDetailRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 팬 앱 에셋 조회 (API 명세 §11.1 · §11.2).
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

    /**
     * 가격을 고를 국가 (§11.2).
     *
     * <p>국가 협상이 아직 없어 KR 고정이다. {@code AdminAssetService} 에도 같은 값이
     * 있지만 <b>공유하지 않는다</b> — 그쪽은 어드민이 가격을 넣을 때의 기본 국가이고
     * 여기는 팬에게 보여 줄 국가다. 나라가 늘면 둘은 다른 방식으로 정해진다(어드민은
     * 선택, 팬은 계정·요청 기준).
     */
    private static final String PRICE_COUNTRY_CODE = "KR";

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

    /**
     * §11.2 에셋 상세.
     *
     * <p><b>안 보이는 에셋은 전부 404 다</b> — 미존재와 "판매 중단 + 비구매자"가 같은
     * {@code ASSET_NOT_FOUND} 다(§1.8 존재 은닉). 403 을 주면 그 에셋이 존재한다는 사실이
     * 응답에서 읽힌다.
     *
     * @throws BusinessException {@code ASSET_NOT_FOUND}
     */
    public AssetDetailResponse detail(Integer userSeq, Integer assetSeq) {
        AssetDetailRow row = motifAssetRepository
                .findDetail(userSeq, assetSeq, PRICE_COUNTRY_CODE)
                .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));

        AssetType assetType = AssetType.valueOf(row.getAssetType());
        MyAccess myAccess = MyAccess.of(row.getOwned(), row.getPassActive(), assetType);
        return new AssetDetailResponse(
                row.getAssetSeq(),
                new AssetDetailResponse.Artist(
                        row.getArtistSeq(),
                        row.getArtistName(),
                        row.getGroupName(),
                        mediaUrlResolver.resolve(row.getArtistProfileImageKey())),
                row.getTitle(),
                // 키를 조립하지 않는다 — 가공이 끝나면 app-media 가 video_key 를 마스터
                // 재생목록으로 덮어쓴다. 서버가 master.m3u8 을 붙이면 가공 전 에셋에
                // 존재하지 않는 주소를 만들어 준다
                mediaUrlResolver.resolve(row.getVideoKey()),
                mediaUrlResolver.resolve(row.getThumbnailKey()),
                row.getDescription(),
                row.getDurationMs(),
                assetType,
                price(row),
                purchasable(row, myAccess),
                myAccess);
    }

    /** KR 활성 행이 없으면 {@code null} — 그때 {@code purchasable} 도 {@code false} 다. */
    private AssetDetailResponse.Price price(AssetDetailRow row) {
        BigDecimal amount = row.getPriceAmount();
        if (amount == null) {
            return null;
        }
        // toPlainString — NUMERIC 을 부동소수로 보내지 않는다는 계약이 여기서 지켜진다.
        // toString 은 지수 표기를 낼 수 있다
        return new AssetDetailResponse.Price(amount.toPlainString(), row.getPriceCurrency());
    }

    /**
     * 지금 살 수 있는가 (기본안 — 원본 §11.2 는 "가격이 없으면 false" 만 정했다).
     *
     * <p><b>이미 산 것은 다시 못 산다.</b> {@code uq_asset_purchases_active} 가
     * (회원, 에셋)의 {@code ACTIVE} 구매를 하나로 막으므로 {@code true} 를 주면 눌렀을 때
     * 실패한다.
     *
     * <p><b>{@code PASS} 는 살 수 있다.</b> 패스는 만료되므로 지금 열려 있어도 영구
     * 보유를 따로 사는 선택이 남아 있어야 한다 — 패스가 끝나는 순간 구매 버튼이 갑자기
     * 생기는 화면이 더 이상하다.
     *
     * <p><b>판매 중단 에셋은 구매자에게만 보이지만, 보인다고 다시 살 수 있는 것은
     * 아니다</b> — 그 사람은 이미 {@code OWNED} 라 첫 조건에서 걸리지만, 판매 중단 판정을
     * 따로 두지 않으면 나중에 "구매자 외에도 보이는" 규칙이 생겼을 때 조용히 열린다.
     */
    private boolean purchasable(AssetDetailRow row, MyAccess myAccess) {
        return Boolean.TRUE.equals(row.getActive())
                && row.getPriceAmount() != null
                && myAccess != MyAccess.OWNED;
    }
}

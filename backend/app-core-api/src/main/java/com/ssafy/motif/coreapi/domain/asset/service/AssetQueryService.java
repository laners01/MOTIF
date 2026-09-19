package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetDetailResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository.AssetDetailRow;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository.AssetPurchasedRow;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 팬 앱 에셋 조회 (API 명세 §11.1 · §11.2).
 *
 * <p><b>둘 다 조회 한 번으로 끝난다.</b> 대상 판정이 전부 쿼리 안에 있어, 여기서 하는
 * 일은 키를 URL 로 바꾸고 §11.2 의 {@link MyAccess}·{@code purchasable} 을 정하는 것뿐이다.
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
    private final MotifPassPriceRepository motifPassPriceRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * 에셋 하나에 대한 이 회원의 이용 권한 — <b>조회 화면 밖에서 묻는 자리</b> (§13.1).
     *
     * <p>{@link #detail} 과 답이 같아야 하지만 <b>노출 규칙을 쓰지 않는다</b>. 상세는
     * 판매 중단 에셋을 비구매자에게 404 로 숨기는데(§11.2), 합성 잡은 "비활성 에셋이어도
     * 권한이 있으면 진행" 이다(DB §37 MOTIF 잡 규칙) — 어제 산 에셋이 오늘 판매 중단됐다고
     * 내 촬영이 막히면 안 된다. 그래서 노출이 아니라 <b>권한만</b> 본다.
     *
     * <p>규칙 자체는 {@link MyAccess#of} 하나다 — {@code OWNED > PASS > NONE},
     * {@code PREMIUM} 은 패스로 열리지 않는다. 여기서 다시 쓰면 숍은 {@code PASS} 라는데
     * 촬영은 막히는 순간이 생긴다.
     *
     * <p>패스 조회는 개별 구매가 없을 때만 한다 — {@code OWNED} 가 먼저 답하므로
     * 보유자에게 패스를 물을 이유가 없다({@code ProductAvailability} 와 같은 판단).
     *
     * @throws BusinessException 404 {@code ASSET_NOT_FOUND}
     */
    public MyAccess myAccess(Integer userSeq, Integer assetSeq) {
        MotifAsset asset = motifAssetRepository.findById(assetSeq)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND, "assetSeq: " + assetSeq));
        boolean owned = motifAssetRepository.existsActivePurchase(userSeq, assetSeq);
        boolean passActive = !owned && motifPassPriceRepository
                .findActivePassExpiry(userSeq, asset.getArtistSeq()).isPresent();
        return MyAccess.of(owned, passActive, asset.getAssetType());
    }

    /**
     * §11.1 내 보유함 — <b>내가 구매한 에셋 전량</b>, 구매 최신순.
     *
     * <p><b>페이지가 없다</b>(S15P21D104-219). 보유함은 한 사람이 산 것이라 커지는 속도가
     * 카탈로그와 다르고, 화면도 한 번에 펼쳐 놓는다. 숍 첫 화면이던 자리는 §11.3 이
     * 가져갔다.
     *
     * <p>실패 분기가 없다. 구매가 0건이든, 없는 {@code artistSeq} 를 받든 빈 목록 200 이다 —
     * 아티스트 존재를 여기서 검증하면 없는 번호로 존재 여부를 떠보는 경로가 열린다.
     *
     * @param artistSeq {@code null} 이면 전체 — 값이 있으면 그 아티스트에게서 산 것만
     */
    public ListResponse<AssetCatalogItem> assets(Integer userSeq, Integer artistSeq) {
        return ListResponse.of(motifAssetRepository.findPurchased(userSeq, artistSeq).stream()
                .map(this::item)
                .toList());
    }

    private AssetCatalogItem item(AssetPurchasedRow row) {
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
                AssetType.valueOf(row.getAssetType()));
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

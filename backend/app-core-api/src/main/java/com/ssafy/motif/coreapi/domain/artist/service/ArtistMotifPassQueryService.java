package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistMotifPassResponse;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.country.CountryRepository;
import com.ssafy.motif.domain.core.pass.MotifPassPrice;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 아티스트별 모티프 패스 조회 (API 명세 §11.4).
 *
 * <p><b>{@link ArtistQueryService} 에 섞지 않는다.</b> 이 패키지는 이미 조회 · 랭킹 ·
 * 구독 셋으로 갈려 있고, 패스는 그중 어디에도 속하지 않는 네 번째 관심사다 — 상품과
 * 보유 상태를 다룬다.
 *
 * <p><b>보유 판정은 {@link MotifPassPriceRepository#findActivePassExpiry} 하나다</b> —
 * §5.3 아티스트 상세가 쓰는 것과 같은 메서드다. 같은 질문에 두 곳이 답하면 상세 화면과
 * 구매 화면이 서로 다른 말을 하는 순간이 생긴다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistMotifPassQueryService {

    /**
     * 가격을 고를 국가 (§11.4).
     *
     * <p>국가 협상이 아직 없어 KR 고정이다 — 시드도 KR 1행뿐이다(changeSet 0013-02).
     * {@code AssetQueryService}·{@code AdminAssetService} 에도 같은 값이 있지만
     * <b>공유하지 않는다</b>. 나라가 늘면 어드민은 운영자 선택으로, 팬 API 는 계정·요청
     * 기준으로 정해질 값이라 같은 상수를 보고 있으면 한쪽 변경이 다른 쪽을 흔든다.
     */
    private static final String PRICE_COUNTRY_CODE = "KR";

    private final ArtistRepository artistRepository;
    private final CountryRepository countryRepository;
    private final MotifPassPriceRepository motifPassPriceRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §11.4 아티스트별 패스 — 배너 · 보유 상태 · 판매 중인 가격.
     *
     * <p><b>보유 중이면 가격을 조회하지 않는다.</b> 응답이 빈 배열로 정해져 있는데
     * (만료 전 연장 구매는 현재 범위가 아니다) 쿼리를 쏠 이유가 없다.
     *
     * @throws BusinessException {@code ARTIST_NOT_FOUND} — 미존재·삭제 아티스트
     */
    public ArtistMotifPassResponse motifPass(Integer userSeq, Integer artistSeq) {
        Artist artist = artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)
                .orElseThrow(() -> new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND));

        String bannerImageUrl = mediaUrlResolver.resolve(artist.getMotifBgImageKey());

        Instant expiresDttm = motifPassPriceRepository
                .findActivePassExpiry(userSeq, artistSeq)
                .orElse(null);
        if (expiresDttm != null) {
            return ArtistMotifPassResponse.held(artistSeq, bannerImageUrl, expiresDttm);
        }

        return ArtistMotifPassResponse.available(artistSeq, bannerImageUrl, prices(artistSeq));
    }

    /**
     * 지금 살 수 있는 가격 — 없으면 빈 배열이다(판매 준비 중인 아티스트가 그렇다).
     *
     * <p>꺼진 가격({@code is_active=FALSE})은 나오지 않는다. 내보내면 눌러도 결제가
     * 시작되지 않는 버튼이 생긴다.
     */
    private List<ArtistMotifPassResponse.Price> prices(Integer artistSeq) {
        Integer countrySeq = countryRepository.findByCountryCode(PRICE_COUNTRY_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "기준 국가 행이 없다 — countries 시드(0013-02)가 적용됐는지 확인하라: "
                                + PRICE_COUNTRY_CODE))
                .getCountrySeq();

        return motifPassPriceRepository
                .findByArtistSeqAndCountrySeqAndActiveTrueOrderByDurationMonthsAsc(
                        artistSeq, countrySeq)
                .stream()
                .map(ArtistMotifPassQueryService::price)
                .toList();
    }

    private static ArtistMotifPassResponse.Price price(MotifPassPrice price) {
        // toPlainString — 지수 표기를 내지 않는다(§11.2 와 같은 계약)
        return new ArtistMotifPassResponse.Price(
                price.getDurationMonths(), price.getAmount().toPlainString(), price.getCurrency());
    }
}

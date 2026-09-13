package com.ssafy.motif.coreapi.admin.pass;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceListResponse;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceRequest;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.country.Country;
import com.ssafy.motif.domain.core.country.CountryRepository;
import com.ssafy.motif.domain.core.pass.MotifPassPrice;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 아티스트 패스 가격 조회·upsert (어드민 명세 §3.9) — S15P21D104-569.
 *
 * <p><b>소급 적용이 없다.</b> 가격 변경은 갱신 이후 주문부터 적용되고 이미 취득한
 * ACTIVE PASS 는 만료까지 그대로다 — 결제 금액은 {@code orders} 스냅샷이 갖는다(DB §32).
 * 그래서 이 서비스는 {@code motif_passes} 를 <b>세기만 하고 건드리지 않는다</b>.
 *
 * <p>활성 패스가 있는 가격을 내리는 것을 <b>막지 않는다</b> — 판매 중단은 정당한 운영
 * 행위이고 기존 이용자는 만료까지 영향이 없다. 대신 응답에 {@code activePassCount} 를
 * 실어 화면이 "몇 명이 쓰는 상품인지" 보여 준다(569 요점 3 — 강제 차단 아님).
 */
@Service
@RequiredArgsConstructor
public class AdminPassPriceService {

    private final MotifPassPriceRepository passPriceRepository;
    private final ArtistRepository artistRepository;
    private final CountryRepository countryRepository;
    private final AdminAuditLogger auditLogger;

    /** §3.9.1 — 설정된 행만 준다. 없는 조합은 화면이 "미설정" 빈 칸으로 그린다. */
    @Transactional(readOnly = true)
    public PassPriceListResponse list(int artistSeq) {
        requireArtist(artistSeq);
        Map<Integer, String> countryCodes = countryRepository.findAll().stream()
                .collect(Collectors.toMap(Country::getCountrySeq, Country::getCountryCode));

        return new PassPriceListResponse(
                passPriceRepository.findByArtistSeqOrderByCountrySeqAscDurationMonthsAsc(artistSeq).stream()
                        .map(price -> toResponse(price, countryCodes.get(price.getCountrySeq())))
                        .toList());
    }

    /** §3.9.2 — {@code (artist, country, duration)} UNIQUE 기준 upsert. */
    @Transactional
    public PassPriceResponse upsert(AuthenticatedAdmin admin, int artistSeq, String countryCode,
                                    short durationMonths, PassPriceRequest request) {
        if (durationMonths <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "durationMonths 는 1 이상이어야 한다: " + durationMonths);
        }
        requireArtist(artistSeq);   // DB §32 — 활성 전환은 아티스트 비삭제가 전제다
        Country country = countryRepository.findByCountryCode(countryCode)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.COUNTRY_NOT_FOUND,
                        "미존재 국가: countryCode=" + countryCode));

        boolean active = request.isActive() == null || request.isActive();
        if (active && !country.isActive()) {
            throw new BusinessException(AdminErrorCode.PRICE_ACTIVATION_BLOCKED,
                    "가격 활성 조건 미충족: country=%s countryActive=false".formatted(countryCode),
                    Map.of("countryActive", false));
        }

        // ponytail: 조회 후 분기라 동시 upsert 는 UNIQUE 위반(500)이 된다. 어드민은 테일넷
        // 한정·팀원 수 명이라 실질 위험이 없다. 다중 운영자가 붙으면 네이티브
        // INSERT ... ON CONFLICT DO UPDATE 한 문장으로 바꾼다
        MotifPassPrice price = passPriceRepository
                .findByArtistSeqAndCountrySeqAndDurationMonths(artistSeq, country.getCountrySeq(), durationMonths)
                .orElse(null);
        if (price == null) {
            price = passPriceRepository.save(MotifPassPrice.of(artistSeq, country.getCountrySeq(),
                    durationMonths, request.amount(), request.currency(), active, admin.userSeq()));
        } else {
            price.update(request.amount(), request.currency(), active, admin.userSeq());
        }

        auditLogger.write(admin, "UPSERT_PASS_PRICE", "ARTIST", artistSeq, AdminAuditLogger.RESULT_OK);
        return toResponse(price, countryCode);
    }

    private PassPriceResponse toResponse(MotifPassPrice price, String countryCode) {
        return PassPriceResponse.of(price, countryCode,
                passPriceRepository.countActivePasses(price.getArtistSeq(), price.getDurationMonths()));
    }

    private void requireArtist(int artistSeq) {
        if (!isArtistAlive(artistSeq)) {
            throw new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                    "미존재·삭제 아티스트: artistSeq=" + artistSeq);
        }
    }

    private boolean isArtistAlive(int artistSeq) {
        return artistRepository.countByArtistSeqInAndDeletedFalse(List.of(artistSeq)) == 1;
    }
}

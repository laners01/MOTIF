package com.ssafy.motif.coreapi.admin.asset;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetActiveResponse;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetCreateRequest;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetCreateResponse;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetDetailResponse;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetUpdateRequest;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetListItem;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetPriceRequest;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetPriceResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.media.VideoRules;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaJobPublisher;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetPrice;
import com.ssafy.motif.domain.core.asset.MotifAssetPriceRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.country.Country;
import com.ssafy.motif.domain.core.country.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 에셋 등록·활성 전환·국가 가격 upsert (어드민 명세 §3.8.2·§3.8.5·§3.8.6) — S15P21D104-568.
 *
 * <p><b>가격 없는 에셋을 만들지 않는다.</b> 등록은 {@code motif_assets} INSERT 와 KR
 * {@code motif_asset_prices} INSERT 를 <b>한 트랜잭션</b>에 넣는다 — 가격 INSERT 가 실패하면
 * 에셋도 남지 않으므로 "활성인데 팔 수 없는" 상태가 구조적으로 생기지 않는다.
 *
 * <p><b>알림 이벤트는 발행하지 않는다</b> — 에셋 등록은 푸시 대상이 아니다(§1.6 — 홈
 * 활동 피드에 자연 노출). <b>{@code job.media} 는 발행한다</b>: 에셋 미리보기도 앱에서
 * 재생되므로 HLS 대상이다(2026-09-17 결정, API 명세 §1.7). 발행은 {@link #create} 와
 * {@link #update} 의 영상 교체 분기 두 곳뿐이고, 커밋 후에 {@code MediaJobEventRelay}
 * 가 실제로 내보낸다 — 행이 없는데 워커가 먼저 도착하면 갱신할 행을 못 찾는다.
 *
 * <p><b>활성 전환과 가격 활성의 방향</b> — 에셋 비활성화는 가격을 전부 내리고(DB §30),
 * 가격 활성화는 에셋이 활성일 때만 허용한다(DB §31). 그래서 순서는 언제나
 * <b>에셋 활성 → 가격 활성</b>이다. 티켓 568 이 적은 "에셋 활성 전환 시 활성 가격이
 * 없으면 409" 를 그대로 넣으면 이 방향과 맞물려 <b>영영 되살릴 수 없는 에셋</b>이
 * 된다(가격은 에셋이 활성이어야 켜지고, 에셋은 가격이 켜져야 활성이 되므로). 의도인
 * "가격 없이 팔리는 상태 금지"는 ① 등록 시 KR 가격 원자 생성 ② 가격 활성 조건 검사
 * ({@code PRICE_ACTIVATION_BLOCKED}) 두 곳이 지키고, 재활성화는 명세 §3.8.5 대로
 * 경고 카운트({@code inactivePriceCount})만 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class AdminAssetService {

    /**
     * 등록 시 가격을 다는 국가 (§3.8.2 — "KR 가격 원자 생성").
     *
     * <p>현재 {@code countries} 시드가 KR 한 행이고 사용자 국가 판정도 상수 KR 이다
     * ({@code 0013-seed.sql}). 다국가가 열리면 요청 필드로 올린다.
     */
    private static final String DEFAULT_COUNTRY_CODE = "KR";

    private final MotifAssetRepository assetRepository;
    private final MotifAssetPriceRepository priceRepository;
    private final ArtistRepository artistRepository;
    private final CountryRepository countryRepository;
    private final AdminMediaService adminMediaService;
    private final VideoRules videoRules;
    private final PublicMediaUrlResolver mediaUrlResolver;
    private final MediaJobPublisher mediaJobPublisher;
    private final AdminAuditLogger auditLogger;

    /**
     * §3.8.1 — 목록. 아티스트·활성·타입 필터, offset 페이지.
     *
     * <p>쿼리 한 번이다 — 아티스트 이름·그룹·KR 가격·구매 수·결과물 수가 전부 다른
     * 테이블이라, 행마다 채우면 한 페이지에 수십 번을 더 왕복한다
     * ({@link MotifAssetRepository#search}).
     */
    @Transactional(readOnly = true)
    public PageResponse<AssetListItem> list(Integer artistSeq, Boolean isActive,
                                            AssetType assetType, Pageable pageable) {
        return PageResponses.of(assetRepository
                .search(null, artistSeq, isActive, assetType == null ? null : assetType.name(),
                        DEFAULT_COUNTRY_CODE, pageable)
                .map(this::listItem));
    }

    private AssetListItem listItem(MotifAssetRepository.AssetListRow row) {
        return new AssetListItem(
                row.getAssetSeq(),
                new AssetListItem.ArtistSummary(row.getArtistSeq(), row.getArtistName(), row.getGroupName()),
                row.getTitle(),
                mediaUrlResolver.resolve(row.getThumbnailKey()),
                row.getDurationMs(),
                AssetType.valueOf(row.getAssetType()),
                row.getActive(),
                // KR 가격 행이 없으면 price 자체가 null 이다 — 0원과 구분돼야 한다(§3.8.1)
                row.getPriceAmount() == null ? null : new AssetPriceResponse(
                        DEFAULT_COUNTRY_CODE, row.getPriceAmount(),
                        row.getPriceCurrency(), row.getPriceActive()),
                row.getPurchaseCount(),
                row.getResultCount(),
                row.getRegDttm());
    }

    /** §3.8.2 — 에셋과 KR 가격을 한 트랜잭션에 만든다. */
    /**
     * §3.8.3 — 상세. 목록과 <b>같은 쿼리</b>를 {@code assetSeq} 로 좁혀 쓴다.
     *
     * <p>조인이 똑같아서(아티스트·그룹·KR 가격·구매 수·결과물 수) 전용 쿼리를 따로
     * 두면 두 벌이 갈라진다 — 한쪽만 고쳤을 때 목록과 상세가 다른 값을 보여준다.
     */
    @Transactional(readOnly = true)
    public AssetDetailResponse detail(int assetSeq) {
        // 존재 확인은 asset() 이 한다 — 목록 쿼리의 빈 결과를 404 로 옮기는 것보다
        // 같은 예외를 한 곳에서 내는 편이 메시지가 갈리지 않는다
        MotifAsset asset = asset(assetSeq);
        MotifAssetRepository.AssetListRow row = assetRepository
                .search(assetSeq, null, null, null, DEFAULT_COUNTRY_CODE, Pageable.ofSize(1))
                .stream().findFirst().orElseThrow();
        // videoKey 는 목록 쿼리에 없다(§3.8.1 항목이 아니다) — 엔티티에서 읽는다.
        // 상세는 한 건이라 왕복 하나가 더 붙어도 목록처럼 행마다 곱해지지 않는다
        return detailOf(row, asset.getVideoKey());
    }

    /**
     * §3.8.4 — 부분 수정. 보낸 필드만 반영한다.
     *
     * <p><b>영상 교체는 메타 3종을 함께 받는다.</b> core-api 에는 ffmpeg 가 없어 서버가
     * 영상을 열어 볼 수 없다(v0.5) — 화면이 {@code <video>} 로 읽어 실어 보낸 값을
     * {@code VideoRules} 로 대조만 한다. 등록(§3.8.2)과 같은 규약이다.
     *
     * <p>영상을 바꾸면 {@code job.media} 를 다시 발행한다. 교체본은 <b>새 uuid</b> 라
     * 프리픽스가 통째로 새것이므로, 산출물에 붙는 1년 {@code immutable} 과 부딪히지
     * 않는다(같은 프리픽스에 다시 쓰면 CDN 무효화가 필수다 — {@code MediaStorage.uploadTree}).
     *
     * <p>가공이 끝나기 전까지 {@code videoKey} 는 업로드 MP4 이고 썸네일은 404 다.
     * 등록과 똑같은 상태이므로 여기서 따로 막지 않는다(§8 #19).
     */
    @Transactional
    public AssetDetailResponse update(AuthenticatedAdmin admin, int assetSeq,
                                      AssetUpdateRequest request) {
        if (request.videoMetaMissing()) {
            throw new BusinessException(com.ssafy.motif.common.error.CommonErrorCode.INVALID_PARAMETER,
                    "영상을 바꾸려면 durationMs·width·height 가 함께 와야 한다");
        }
        MotifAsset asset = asset(assetSeq);
        asset.update(request.title(), request.description(), request.assetType());
        if (request.hasVideo()) {
            // 등록과 같은 순서 — 규격 먼저, HEAD 는 마지막. 왕복이 제일 비싸다(§1.4)
            videoRules.checkAsset(new VideoRules.Meta(
                    request.durationMs(), request.width(), request.height()));
            requireUploaded(request.videoKey());
            asset.replaceVideo(request.videoKey(),
                    MediaKeys.forAssetThumbnail(request.videoKey()),
                    Math.toIntExact(request.durationMs()));
            publishMediaJob(assetSeq, request.videoKey());
        }

        auditLogger.write(admin, "UPDATE_ASSET", "ASSET", assetSeq, AdminAuditLogger.RESULT_OK);
        // 갱신된 값으로 다시 읽는다 — 구매 수·결과물 수처럼 이 트랜잭션이 안 건드린
        // 집계도 함께 돌려줘야 화면이 응답만으로 행을 다시 그릴 수 있다
        return detail(assetSeq);
    }

    private AssetDetailResponse detailOf(MotifAssetRepository.AssetListRow row, String videoKey) {
        AssetListItem item = listItem(row);
        return new AssetDetailResponse(
                item.assetSeq(), item.artist(), item.title(), row.getDescription(),
                item.thumbnailUrl(), videoKey, item.durationMs(), item.assetType(),
                item.isActive(), item.price(), item.purchaseCount(), item.resultCount(),
                item.regDttm());
    }

    @Transactional
    public AssetCreateResponse create(AuthenticatedAdmin admin, AssetCreateRequest request) {
        if (artistRepository.countByArtistSeqInAndDeletedFalse(List.of(request.artistSeq())) != 1) {
            throw new BusinessException(ArtistErrorCode.ARTIST_NOT_FOUND,
                    "미존재·삭제 아티스트: artistSeq=" + request.artistSeq());
        }
        // 트랜잭션 진입 전 검증 2종 (§1.4) — 규격 먼저 보고 HEAD 는 마지막에, 왕복이 제일 비싸다
        videoRules.checkAsset(new VideoRules.Meta(request.durationMs(), request.width(), request.height()));
        requireUploaded(request.videoKey());
        Country country = country(DEFAULT_COUNTRY_CODE);

        MotifAsset asset = assetRepository.save(MotifAsset.register(
                request.artistSeq(), request.title(), request.description(),
                request.videoKey(), MediaKeys.forAssetThumbnail(request.videoKey()),
                (int) request.durationMs(), request.assetType()));
        priceRepository.save(MotifAssetPrice.of(asset.getAssetSeq(), country.getCountrySeq(),
                request.price().amount(), request.price().currency(), true, admin.userSeq()));

        publishMediaJob(asset.getAssetSeq(), request.videoKey());

        auditLogger.write(admin, "CREATE_ASSET", "ASSET", asset.getAssetSeq(), AdminAuditLogger.RESULT_OK);
        return new AssetCreateResponse(asset.getAssetSeq(), asset.getDurationMs(),
                mediaUrlResolver.resolve(asset.getThumbnailKey()));
    }

    /**
     * 후처리 요청 — 렌디션·HLS·썸네일. 챌린지 등록과 같은 모양이다.
     *
     * <p>썸네일 시각을 넘기지 않는다. 에셋 등록 화면에는 스크럽 UI 가 없어 어드민이
     * 고를 값이 없고, 워커 기본값(1.5초)이면 ≤20초짜리 미리보기에 충분하다.
     */
    private void publishMediaJob(int assetSeq, String videoKey) {
        mediaJobPublisher.publish(
                MediaJobRequestedEvent.SOURCE_ASSET_PREVIEW,
                assetSeq,
                videoKey,                                   // assets/{uuid}.mp4
                MediaKeys.forAssetPreview(videoKey),        // assets/{uuid}
                null);
    }

    /** §3.8.5 — 비활성화는 가격까지 내리고(DB §30), 재활성화는 가격을 되살리지 않는다. */
    @Transactional
    public AssetActiveResponse changeActive(AuthenticatedAdmin admin, int assetSeq, boolean active) {
        MotifAsset asset = asset(assetSeq);
        if (asset.isActive() == active) {
            throw new BusinessException(AdminErrorCode.ASSET_STATE_UNCHANGED,
                    "이미 같은 상태: assetSeq=%d isActive=%s".formatted(assetSeq, active));
        }
        asset.changeActive(active);
        if (!active) {
            priceRepository.deactivateAll(assetSeq, admin.userSeq(), Instant.now());
        }

        auditLogger.write(admin, active ? "ACTIVATE_ASSET" : "DEACTIVATE_ASSET",
                "ASSET", assetSeq, AdminAuditLogger.RESULT_OK);
        return new AssetActiveResponse(assetSeq, active,
                priceRepository.countByAssetSeqAndActiveFalse(assetSeq));
    }

    /** §3.8.6 — {@code (asset_seq, country_seq)} UNIQUE 기준 upsert. */
    @Transactional
    public AssetPriceResponse upsertPrice(AuthenticatedAdmin admin, int assetSeq,
                                          String countryCode, AssetPriceRequest request) {
        MotifAsset asset = asset(assetSeq);
        Country country = country(countryCode);
        boolean active = request.isActive() == null || request.isActive();
        if (active && !(asset.isActive() && country.isActive())) {
            throw new BusinessException(AdminErrorCode.PRICE_ACTIVATION_BLOCKED,
                    "가격 활성 조건 미충족: assetSeq=%d assetActive=%s country=%s countryActive=%s"
                            .formatted(assetSeq, asset.isActive(), countryCode, country.isActive()),
                    Map.of("assetActive", asset.isActive(), "countryActive", country.isActive()));
        }

        // ponytail: 조회 후 분기라 동시 upsert 는 UNIQUE 위반(500)이 된다. 어드민은 테일넷
        // 한정·팀원 수 명이라 실질 위험이 없다. 다중 운영자가 붙으면 네이티브
        // INSERT ... ON CONFLICT DO UPDATE 한 문장으로 바꾼다
        MotifAssetPrice price = priceRepository
                .findByAssetSeqAndCountrySeq(assetSeq, country.getCountrySeq())
                .orElse(null);
        if (price == null) {
            price = priceRepository.save(MotifAssetPrice.of(assetSeq, country.getCountrySeq(),
                    request.amount(), request.currency(), active, admin.userSeq()));
        } else {
            price.update(request.amount(), request.currency(), active, admin.userSeq());
        }

        auditLogger.write(admin, "UPSERT_ASSET_PRICE", "ASSET", assetSeq, AdminAuditLogger.RESULT_OK);
        return AssetPriceResponse.of(price, countryCode);
    }

    private MotifAsset asset(int assetSeq) {
        return assetRepository.findById(assetSeq)
                .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND,
                        "미존재 에셋: assetSeq=" + assetSeq));
    }

    private Country country(String countryCode) {
        return countryRepository.findByCountryCode(countryCode)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.COUNTRY_NOT_FOUND,
                        "미존재 국가: countryCode=" + countryCode));
    }

    private void requireUploaded(String videoKey) {
        if (!adminMediaService.exists(videoKey)) {
            throw new BusinessException(AdminErrorCode.MEDIA_NOT_UPLOADED,
                    "S3 에 없는 영상 키: " + videoKey, Map.of("videoKey", videoKey));
        }
    }
}

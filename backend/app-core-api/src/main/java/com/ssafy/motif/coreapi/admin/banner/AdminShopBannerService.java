package com.ssafy.motif.coreapi.admin.banner;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerCreateRequest;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerCreateResponse;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerItem;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerOrderRequest;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerUpdateRequest;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.banner.ShopBanner;
import com.ssafy.motif.domain.core.banner.ShopBannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 숍 공용 배너 (어드민 명세 §3.11) — S15P21D104-585·586.
 *
 * <p><b>페이지네이션이 없다.</b> 숍 상단에 몇 개만 걸리는 화면 요소라 전량을 준다
 * (§3.11.1). 화면도 재정렬 때문에 전량이 필요하다 — 한 페이지만 들고 순서를 바꾸면
 * 보이지 않는 행의 자리가 어디로 가는지 정해지지 않는다.
 *
 * <p><b>순서는 한 건씩 옮기지 않는다.</b> {@code display_order} 가 UNIQUE 라
 * 한 건만 바꾸면 반드시 다른 행과 부딪힌다(1↔2 맞바꾸기조차 걸린다). 그래서 §3.11.3
 * 수정에는 순서가 없고 재정렬은 전량 교체(§3.11.4)뿐이다.
 */
@Service
@RequiredArgsConstructor
public class AdminShopBannerService {

    /**
     * 재정렬 1단계에서 밀어 올리는 폭.
     *
     * <p>현재 최대 순서보다 크기만 하면 된다. 순서는 재정렬 때마다 0..n−1 로 다시
     * 깔리므로 최대값이 배너 수를 넘지 않고, {@code display_order} 가 SMALLINT
     * (최대 32767)라 배너가 22767개를 넘지 않는 한 넘치지 않는다.
     */
    private static final short ORDER_SHIFT = 10000;

    private final ShopBannerRepository bannerRepository;
    private final AdminMediaService adminMediaService;
    private final PublicMediaUrlResolver mediaUrlResolver;
    private final AdminAuditLogger auditLogger;

    /** §3.11.1 — 전량(비활성 포함), 순서 오름차순. */
    @Transactional(readOnly = true)
    public ListResponse<BannerItem> list() {
        return ListResponse.of(bannerRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::item)
                .toList());
    }

    /** §3.11.2 — {@code displayOrder} 생략 시 맨 뒤(MAX+1). */
    @Transactional
    public BannerCreateResponse create(AuthenticatedAdmin admin, BannerCreateRequest request) {
        requireUploaded(request.imageKey());

        short order = request.displayOrder() == null ? nextOrder() : request.displayOrder();
        // 생략분(MAX+1)은 부딪힐 수 없지만 지정분은 부딪힌다. UNIQUE 위반을 500 으로
        // 흘리지 않고 §5 코드로 돌려준다 — 어드민이 고칠 수 있는 입력 오류다
        if (bannerRepository.existsByDisplayOrder(order)) {
            throw new BusinessException(AdminErrorCode.BANNER_ORDER_DUPLICATE,
                    "이미 사용 중인 순서: displayOrder=" + order,
                    Map.of("displayOrder", order));
        }

        ShopBanner banner = bannerRepository.save(ShopBanner.register(request.imageKey(), order));

        auditLogger.write(admin, "CREATE_BANNER", "BANNER",
                banner.getShopBannerSeq(), AdminAuditLogger.RESULT_OK);
        return new BannerCreateResponse(banner.getShopBannerSeq());
    }

    /** §3.11.3 — 이미지 교체·활성 전환. 보낸 필드만 반영한다. */
    @Transactional
    public BannerItem update(AuthenticatedAdmin admin, int shopBannerSeq, BannerUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "imageKey·isActive 중 적어도 하나는 있어야 한다: shopBannerSeq=" + shopBannerSeq);
        }
        ShopBanner banner = banner(shopBannerSeq);
        if (request.imageKey() != null) {
            requireUploaded(request.imageKey());
            banner.replaceImage(request.imageKey());
        }
        if (request.isActive() != null) {
            banner.changeActive(request.isActive());
        }

        auditLogger.write(admin, "UPDATE_BANNER", "BANNER", shopBannerSeq, AdminAuditLogger.RESULT_OK);
        return item(banner);
    }

    /**
     * §3.11.4 — 전체 재정렬. 배열 순서대로 0..n−1 을 다시 깐다.
     *
     * <p>두 단계로 나눈다: 전 행을 {@link #ORDER_SHIFT} 만큼 밀어 올린 뒤 새 순서를 깐다.
     * 곧바로 쓰면 중간 상태에서 UNIQUE 와 부딪힌다({@code ShopBannerRepository.shiftAllBy}).
     *
     * <p>1단계가 영속성 컨텍스트를 비우므로 <b>엔티티를 다시 읽어야 한다</b> — 비우기 전에
     * 들고 있던 인스턴스에 순서를 넣으면 준영속이라 아무 일도 일어나지 않는다.
     */
    @Transactional
    public ListResponse<BannerItem> reorder(AuthenticatedAdmin admin, BannerOrderRequest request) {
        List<Integer> ordered = request.orderedBannerSeqs();
        Set<Integer> requested = new LinkedHashSet<>(ordered);
        Set<Integer> existing = bannerRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(ShopBanner::getShopBannerSeq)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 중복·누락·미존재를 한 번에 본다 — 전량과 집합이 같아야만 순서가 확정된다
        if (requested.size() != ordered.size() || !requested.equals(existing)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "재정렬은 전량이어야 한다 — 중복·누락·미존재 seq 가 있다",
                    Map.of("requested", ordered, "existing", existing));
        }

        bannerRepository.shiftAllBy(ORDER_SHIFT);
        Map<Integer, ShopBanner> reloaded = bannerRepository.findAllById(ordered).stream()
                .collect(Collectors.toMap(ShopBanner::getShopBannerSeq, Function.identity()));
        short order = 0;
        for (Integer seq : ordered) {
            reloaded.get(seq).moveTo(order++);
        }
        // 2단계의 UPDATE 가 조회보다 먼저 DB 에 닿아야 한다 — 아래 list() 는 같은
        // 트랜잭션의 새 쿼리라 플러시 없이는 밀어 올린 값(10000+)을 읽는다
        bannerRepository.flush();

        // 대상이 전량이라 targetSeq 가 하나로 정해지지 않는다 — 0(전량)으로 두고
        // 새 순서를 reason 에 싣는다. 감사 파일만 보고 무엇이 어떻게 바뀌었는지 읽혀야 한다
        auditLogger.write(admin, "REORDER_BANNERS", "BANNER", 0,
                AdminAuditLogger.RESULT_OK, ordered.toString());
        return list();
    }

    /** §3.11.5 — 하드 삭제. 참조하는 곳이 없다. */
    @Transactional
    public void delete(AuthenticatedAdmin admin, int shopBannerSeq) {
        bannerRepository.delete(banner(shopBannerSeq));

        // 남은 행의 순서를 다시 깔지 않는다 — 구멍이 나도 UNIQUE 는 그대로 성립하고,
        // 순서를 손보고 싶으면 재정렬(§3.11.4)이 있다
        auditLogger.write(admin, "DELETE_BANNER", "BANNER", shopBannerSeq, AdminAuditLogger.RESULT_OK);
    }

    private BannerItem item(ShopBanner banner) {
        return new BannerItem(
                banner.getShopBannerSeq(),
                mediaUrlResolver.resolve(banner.getImageKey()),
                banner.getImageKey(),
                banner.getDisplayOrder(),
                banner.isActive(),
                banner.getRegDttm());
    }

    private short nextOrder() {
        return (short) (bannerRepository.findFirstByOrderByDisplayOrderDesc()
                .map(ShopBanner::getDisplayOrder)
                .orElse((short) -1) + 1);
    }

    private ShopBanner banner(int shopBannerSeq) {
        return bannerRepository.findById(shopBannerSeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.BANNER_NOT_FOUND,
                        "미존재 배너: shopBannerSeq=" + shopBannerSeq));
    }

    private void requireUploaded(String imageKey) {
        if (!adminMediaService.exists(imageKey)) {
            throw new BusinessException(MediaErrorCode.IMAGE_NOT_UPLOADED,
                    "S3 에 없는 이미지 키: " + imageKey, Map.of("imageKey", imageKey));
        }
    }
}

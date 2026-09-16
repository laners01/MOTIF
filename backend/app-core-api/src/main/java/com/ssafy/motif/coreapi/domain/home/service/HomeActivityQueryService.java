package com.ssafy.motif.coreapi.domain.home.service;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.home.dto.ActivityItem;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.artist.SubscribedArtistRow;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.home.ActivityKeyRow;
import com.ssafy.motif.domain.core.home.HomeActivityRepository;
import com.ssafy.motif.domain.core.notice.Notice;
import com.ssafy.motif.domain.core.notice.NoticeImage;
import com.ssafy.motif.domain.core.notice.NoticeImageRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 구독 아티스트 활동 피드 (API 명세 §6.3).
 *
 * <p><b>정렬·페이지는 SQL 이, 조립은 여기가 한다.</b> 두 테이블을 각각 읽어 자바에서
 * 합치면 {@code totalElements} 를 낼 수 없고(offset 페이지 응답의 필수 칸이다),
 * 페이지를 넘길수록 양쪽에서 {@code (page+1) × size} 씩 읽어 버리는 양이 커진다.
 *
 * <p><b>한 페이지에 조회 6번이 상한이다</b> — 구독 · 키 페이지 · 에셋 · 공지 · 이미지 ·
 * 좋아요. 뒤 셋은 그 페이지에 해당 타입이 없으면 건너뛴다. 항목마다 읽으면 20행에
 * 그만큼 왕복이 늘어난다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeActivityQueryService {

    private static final String TYPE_MOTIF_ASSET = "MOTIF_ASSET";

    private final SubscriptionRepository subscriptionRepository;
    private final HomeActivityRepository homeActivityRepository;
    private final MotifAssetRepository motifAssetRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeImageRepository noticeImageRepository;

    /** 공지 이미지·에셋 썸네일·프로필은 전부 공개 등급이다(§1.7). */
    private final PublicMediaUrlResolver mediaUrlResolver;

    /**
     * §6.3 활동 피드 — offset 페이지, 등록 시각 통합 최신순.
     *
     * <p><b>구독 조회가 아티스트 요약까지 함께 해결한다.</b>
     * {@code findSubscribed} 는 §4.6·§4.7 이 쓰는 공용 쿼리인데 이름·그룹명·프로필 키를
     * 이미 싣고 삭제 아티스트를 걸러 준다 — 활동 항목의 {@code artist} 가 정확히 그
     * 구성이라 아티스트를 다시 읽지 않는다.
     *
     * <p><b>구독 0건이면 목록 쿼리를 돌리지 않는다.</b> 빈 {@code in ()} 은 Postgres
     * 문법 오류라 그냥 넘기면 500 이 된다.
     */
    public PageResponse<ActivityItem> activities(Integer userSeq, int page, int size) {
        Pageable pageable = PageResponses.pageable(page, size);

        List<SubscribedArtistRow> subscribed = subscriptionRepository.findSubscribed(userSeq);
        if (subscribed.isEmpty()) {
            return PageResponses.of(new PageImpl<ActivityItem>(List.of(), pageable, 0));
        }

        Map<Integer, ActivityItem.Artist> artists = subscribed.stream()
                .collect(Collectors.toMap(SubscribedArtistRow::artistSeq, this::toArtist));

        Page<ActivityKeyRow> keys =
                homeActivityRepository.findActivities(artists.keySet(), pageable);

        List<ActivityItem> list = assemble(userSeq, keys.getContent(), artists);

        // 총 개수는 키 페이지(count 쿼리)의 것을 쓴다 — 아래 assemble 이 참조가 끊긴 줄을
        // 걸러내 list 가 더 짧을 수 있지만, 그것 때문에 총계를 줄이면 페이지 수가 흔들린다
        return PageResponses.of(new PageImpl<>(list, pageable, keys.getTotalElements()));
    }

    /**
     * 키 목록 → 응답 항목. <b>타입별 배치 조회를 먼저 하고</b> 그 다음 순서대로 조립한다.
     *
     * <p><b>참조가 끊긴 줄은 건너뛴다.</b> 키 조회와 배치 조회는 서로 다른 statement 라,
     * Postgres 기본 격리 수준(READ COMMITTED)에서 그 사이에 에셋이 비활성화되거나 공지가
     * 삭제될 수 있다. 드물지만 그때 {@code null} 을 항목으로 내보내면 클라이언트가
     * 파싱에서 깨진다 — 한 칸 비는 편이 낫다.
     */
    private List<ActivityItem> assemble(Integer userSeq, List<ActivityKeyRow> keys,
                                        Map<Integer, ActivityItem.Artist> artists) {
        List<Integer> assetSeqs = seqsOf(keys, true);
        List<Integer> noticeSeqs = seqsOf(keys, false);

        Map<Integer, MotifAsset> assets = motifAssetRepository.findAllById(assetSeqs).stream()
                .collect(Collectors.toMap(MotifAsset::getAssetSeq, Function.identity()));
        Map<Integer, Notice> notices = noticeRepository.findAllById(noticeSeqs).stream()
                .collect(Collectors.toMap(Notice::getNoticeSeq, Function.identity()));

        // 그 타입이 없는 페이지에서는 건너뛴다 — 빈 in () 을 흘리지 않기 위해서다
        Map<Integer, List<ActivityItem.Image>> images =
                noticeSeqs.isEmpty() ? Map.of() : imagesByNotice(noticeSeqs);
        Set<Integer> liked = noticeSeqs.isEmpty() ? Set.of()
                : Set.copyOf(noticeRepository.findLikedNoticeSeqs(userSeq, noticeSeqs));

        List<ActivityItem> list = new ArrayList<>(keys.size());
        for (ActivityKeyRow key : keys) {
            if (TYPE_MOTIF_ASSET.equals(key.getType())) {
                MotifAsset asset = assets.get(key.getSeq());
                if (asset != null) {
                    list.add(toAssetItem(key, asset, artists));
                }
                continue;
            }
            Notice notice = notices.get(key.getSeq());
            if (notice != null) {
                list.add(toNoticeItem(key, notice, artists, images, liked));
            }
        }
        return list;
    }

    private ActivityItem toAssetItem(ActivityKeyRow key, MotifAsset asset,
                                     Map<Integer, ActivityItem.Artist> artists) {
        return ActivityItem.ofAsset(
                key.getRegDttm(),
                artists.get(asset.getArtistSeq()),
                new ActivityItem.Asset(
                        asset.getAssetSeq(),
                        asset.getTitle(),
                        mediaUrlResolver.resolve(asset.getThumbnailKey()),
                        asset.getDurationMs()));
    }

    private ActivityItem toNoticeItem(ActivityKeyRow key, Notice notice,
                                      Map<Integer, ActivityItem.Artist> artists,
                                      Map<Integer, List<ActivityItem.Image>> images,
                                      Set<Integer> liked) {
        return ActivityItem.ofNotice(
                key.getRegDttm(),
                artists.get(notice.getArtistSeq()),
                new ActivityItem.Notice(
                        notice.getNoticeSeq(),
                        notice.getContent(),
                        images.getOrDefault(notice.getNoticeSeq(), List.of()),
                        notice.getLikeCount(),
                        notice.getCommentCount(),
                        liked.contains(notice.getNoticeSeq())));
    }

    private List<Integer> seqsOf(List<ActivityKeyRow> keys, boolean asset) {
        return keys.stream()
                .filter(key -> TYPE_MOTIF_ASSET.equals(key.getType()) == asset)
                .map(ActivityKeyRow::getSeq)
                .toList();
    }

    /** 한 페이지 분 이미지를 한 번에 읽어 {@code noticeSeq} 로 묶는다 — 항목마다 읽으면 N+1. */
    private Map<Integer, List<ActivityItem.Image>> imagesByNotice(List<Integer> noticeSeqs) {
        return noticeImageRepository.findByNoticeSeqInOrderByNoticeSeqAscDisplayOrderAsc(noticeSeqs)
                .stream()
                .collect(Collectors.groupingBy(
                        NoticeImage::getNoticeSeq,
                        Collectors.mapping(this::toImage, Collectors.toList())));
    }

    private ActivityItem.Image toImage(NoticeImage image) {
        return new ActivityItem.Image(
                mediaUrlResolver.resolve(image.getImageKey()), image.getDisplayOrder());
    }

    private ActivityItem.Artist toArtist(SubscribedArtistRow row) {
        return new ActivityItem.Artist(row.artistSeq(), row.name(), row.groupName(),
                mediaUrlResolver.resolve(row.profileImageKey()));
    }
}

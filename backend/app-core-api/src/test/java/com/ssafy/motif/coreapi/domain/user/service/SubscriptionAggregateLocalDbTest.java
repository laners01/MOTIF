package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §4.7 집계 쿼리가 실제 DB 에서 도는지 (S15P21D104-284).
 *
 * <p><b>네이티브 쿼리는 부트스트랩에서 검증되지 않는다.</b> JPQL 은 컨텍스트가 뜰 때
 * 파싱·검증을 거치지만 네이티브 SQL 은 <b>실행돼야</b> 컬럼명 오타나 문법 오류가
 * 드러난다. 그래서 두 개를 실제로 한 번 돌린다 — 결과가 비어도 SQL 은 실행된다.
 *
 * <p>데이터를 넣지 않는 것은 의도다. 여기서 보려는 것은 "쿼리가 도는가"이고, 집계
 * 규칙(패스는 구매로 안 센다 등)은 {@code UserQueryServiceTest} 가 목으로 고정한다.
 * <b>로컬 compose DB 는 세 레인이 공유하므로</b> 건수 단언도 두지 않는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class SubscriptionAggregateLocalDbTest {

    /** 어떤 레인도 쓰지 않을 값 — 결과가 비어야 정상이다. */
    private static final int ABSENT_USER = -1;
    private static final List<Integer> ABSENT_ARTISTS = List.of(-1, -2);

    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private MotifPassPriceRepository motifPassPriceRepository;
    @Autowired
    private ParticipationVideoRepository participationVideoRepository;

    @Test
    @DisplayName("개별 구매 집계 네이티브 SQL 이 실행된다 (asset_purchases ⋈ motif_assets)")
    void purchasedCountQueryRuns() {
        assertThat(motifAssetRepository.countPurchasedByArtists(ABSENT_USER, ABSENT_ARTISTS))
                .isEmpty();
    }

    @Test
    @DisplayName("유효 패스 조회 네이티브 SQL 이 실행된다 (motif_passes)")
    void activePassQueryRuns() {
        assertThat(motifPassPriceRepository.findActivePasses(ABSENT_USER, ABSENT_ARTISTS))
                .isEmpty();
    }

    @Test
    @DisplayName("§1.8 을 적용한 내 영상 집계 JPQL 이 실행된다 (참여 영상 ⋈ 챌린지)")
    void myVideoCountQueryRuns() {
        assertThat(participationVideoRepository.countMineByArtists(ABSENT_USER, ABSENT_ARTISTS))
                .isEmpty();
    }

    @Test
    @DisplayName("판매 중 에셋 집계 JPQL 이 실행된다")
    void activeAssetCountQueryRuns() {
        assertThat(motifAssetRepository.countActiveByArtists(ABSENT_ARTISTS)).isEmpty();
    }
}

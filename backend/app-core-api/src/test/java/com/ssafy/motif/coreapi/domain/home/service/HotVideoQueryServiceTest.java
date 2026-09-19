package com.ssafy.motif.coreapi.domain.home.service;

import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.home.dto.HotVideoItem;
import com.ssafy.motif.coreapi.domain.video.ranking.VideoRankingSnapshot;
import com.ssafy.motif.coreapi.domain.video.ranking.VideoRankingSnapshot.Ranked;
import com.ssafy.motif.coreapi.global.media.CloudFrontUrlSigner;
import com.ssafy.motif.coreapi.global.media.MediaSigningProperties;
import com.ssafy.motif.coreapi.global.media.MediaSigningTestSupport;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.ProtectedMediaUrlResolver;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.video.HotVideoRow;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * HOT 목록 조회 (명세 §6.1 · S15P21D104-113).
 *
 * <p>여기서 고정하는 것은 <b>커서 왕복과 순서 보존</b>이다 — 스냅샷이 준 순서를 DB
 * 조회가 흐트러뜨리지 않는지, 이어 받은 페이지가 겹치거나 빠지지 않는지.
 */
class HotVideoQueryServiceTest {

    private static final long VERSION = 1788998400000L;
    private static final String CDN = "https://cdn.motif.io";

    private VideoRankingSnapshot snapshot;
    private ParticipationVideoRepository participationVideoRepository;
    private HotVideoQueryService service;

    @BeforeEach
    void setUp() {
        snapshot = mock(VideoRankingSnapshot.class);
        participationVideoRepository = mock(ParticipationVideoRepository.class);

        // HOT 후보도 보호 등급이다(ADR-0013) — 서명 경로를 태우지 않으면 URL 이
        // 통째로 null 이어도 테스트가 통과한다
        CloudFrontUrlSigner signer = new CloudFrontUrlSigner(new MediaSigningProperties(
                "KTESTKEYPAIRID", MediaSigningTestSupport.pkcs8Pem(),
                Duration.ofMinutes(10), Duration.ofHours(6)));
        ReflectionTestUtils.setField(signer, "cdnBaseUrl", CDN);

        service = new HotVideoQueryService(snapshot, participationVideoRepository,
                new MediaUrlResolver(new PublicMediaUrlResolver(CDN),
                        new ProtectedMediaUrlResolver(CDN, signer), signer));
    }

    private static HotVideoRow row(long videoSeq) {
        return new HotVideoRow(videoSeq,
                "private/challenge/uuid-" + videoSeq + "/master.m3u8",
                "private/challenge/uuid-" + videoSeq + "/thumb.jpg",
                15000, 3400L, "bailar toda la noche", "mochico", 5, "팬123");
    }

    @Test
    @DisplayName("스냅샷이 없으면 빈 목록이다 — 배치가 아직 안 돌았을 때")
    void noSnapshotGivesEmptyList() {
        given(snapshot.currentVersion()).willReturn(null);

        CursorResponse<HotVideoItem> response = service.hotVideos(null, 10);

        assertThat(response.list()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서가 없으면 현재 버전의 1위부터 읽는다")
    void firstPageStartsAtRankOne() {
        given(snapshot.currentVersion()).willReturn(VERSION);
        given(snapshot.page(VERSION, 0, 3)).willReturn(List.of(new Ranked(1, 88L)));
        given(participationVideoRepository.findHotCards(List.of(88L))).willReturn(List.of(row(88L)));

        CursorResponse<HotVideoItem> response = service.hotVideos(null, 2);

        assertThat(response.list()).singleElement()
                .satisfies(item -> {
                    assertThat(item.rank()).isEqualTo(1);
                    assertThat(item.videoSeq()).isEqualTo(88L);
                    // 남의 PUBLIC 영상에도 서명이 붙는다 — 인가는 visibility 가 하지
                    // 서명 유무가 하지 않는다(ADR-0013)
                    assertThat(item.videoUrl())
                            .startsWith(CDN + "/private/challenge/uuid-88/master.m3u8?")
                            .contains("Policy=", "Signature=", "Key-Pair-Id=");
                    assertThat(item.song().singerName()).isEqualTo("mochico");
                    assertThat(item.author().nickname()).isEqualTo("팬123");
                });
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("size + 1 을 읽어 넘치면 잘라내고 hasNext 로 쓴다")
    void trimsOverflowAndReportsHasNext() {
        given(snapshot.currentVersion()).willReturn(VERSION);
        given(snapshot.page(VERSION, 0, 3)).willReturn(
                List.of(new Ranked(1, 88L), new Ranked(2, 12L), new Ranked(3, 7L)));
        given(participationVideoRepository.findHotCards(List.of(88L, 12L)))
                .willReturn(List.of(row(88L), row(12L)));

        CursorResponse<HotVideoItem> response = service.hotVideos(null, 2);

        assertThat(response.list()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(Cursors.decodeSnapshot(response.nextCursor()))
                .isEqualTo(new Cursors.Snapshot(VERSION, 2));
    }

    @Test
    @DisplayName("커서를 그대로 넣으면 같은 버전을 이어 읽는다 — 배치가 갈려도 순서가 유지된다")
    void followsCursorVersionNotCurrent() {
        given(snapshot.page(VERSION, 2, 3)).willReturn(List.of(new Ranked(3, 7L)));
        given(participationVideoRepository.findHotCards(List.of(7L))).willReturn(List.of(row(7L)));

        CursorResponse<HotVideoItem> response =
                service.hotVideos(Cursors.encodeSnapshot(VERSION, 2), 2);

        assertThat(response.list()).singleElement()
                .extracting(HotVideoItem::rank).isEqualTo(3);
        // 커서가 있으면 현재 버전을 묻지 않는다 — 물으면 새 스냅샷으로 갈아타 순서가 꼬인다
        org.mockito.Mockito.verify(snapshot, org.mockito.Mockito.never()).currentVersion();
    }

    @Test
    @DisplayName("보존 기간이 지난 버전의 커서는 빈 목록 200 이다 — 1위로 되돌리지 않는다")
    void expiredVersionGivesEmptyList() {
        given(snapshot.page(anyLong(), anyInt(), anyInt())).willReturn(List.of());

        CursorResponse<HotVideoItem> response =
                service.hotVideos(Cursors.encodeSnapshot(VERSION, 40), 10);

        assertThat(response.list()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("DB 가 순서를 섞어 줘도 스냅샷 순서로 낸다")
    void keepsSnapshotOrderRegardlessOfDbOrder() {
        given(snapshot.currentVersion()).willReturn(VERSION);
        given(snapshot.page(VERSION, 0, 4)).willReturn(
                List.of(new Ranked(1, 88L), new Ranked(2, 12L), new Ranked(3, 7L)));
        // in 조회는 입력 순서를 보장하지 않는다
        given(participationVideoRepository.findHotCards(any()))
                .willReturn(List.of(row(7L), row(88L), row(12L)));

        CursorResponse<HotVideoItem> response = service.hotVideos(null, 3);

        assertThat(response.list()).extracting(HotVideoItem::videoSeq)
                .containsExactly(88L, 12L, 7L);
    }

    @Test
    @DisplayName("스냅샷 이후 비노출이 된 영상은 빠지고 순위 번호는 건너뛴다")
    void skipsHiddenVideoAndKeepsRankNumbers() {
        given(snapshot.currentVersion()).willReturn(VERSION);
        given(snapshot.page(VERSION, 0, 4)).willReturn(
                List.of(new Ranked(1, 88L), new Ranked(2, 12L), new Ranked(3, 7L)));
        // 12번은 §1.8 로 조회에서 빠졌다 (삭제·비공개 전환)
        given(participationVideoRepository.findHotCards(any()))
                .willReturn(List.of(row(88L), row(7L)));

        CursorResponse<HotVideoItem> response = service.hotVideos(null, 3);

        assertThat(response.list()).extracting(HotVideoItem::rank).containsExactly(1, 3);
    }

    @Test
    @DisplayName("다음 커서는 빠진 항목까지 포함한 마지막 순위다 — 다시 읽지 않는다")
    void nextCursorUsesSnapshotRankNotListSize() {
        given(snapshot.currentVersion()).willReturn(VERSION);
        given(snapshot.page(VERSION, 0, 3)).willReturn(
                List.of(new Ranked(1, 88L), new Ranked(2, 12L), new Ranked(3, 7L)));
        // 1·2위가 모두 비노출이라 목록은 비지만, 다음 페이지는 2위 다음부터여야 한다
        given(participationVideoRepository.findHotCards(any())).willReturn(List.of());

        CursorResponse<HotVideoItem> response = service.hotVideos(null, 2);

        assertThat(response.list()).isEmpty();
        assertThat(Cursors.decodeSnapshot(response.nextCursor()))
                .isEqualTo(new Cursors.Snapshot(VERSION, 2));
    }

    @Test
    @DisplayName("size 를 100 으로 자른다 (§1.9 커서 목록 공통 상한)")
    void clampsSizeToHundred() {
        given(snapshot.currentVersion()).willReturn(VERSION);
        given(snapshot.page(VERSION, 0, 101)).willReturn(List.of());

        service.hotVideos(null, 500);

        org.mockito.Mockito.verify(snapshot).page(VERSION, 0, 101);
    }
}

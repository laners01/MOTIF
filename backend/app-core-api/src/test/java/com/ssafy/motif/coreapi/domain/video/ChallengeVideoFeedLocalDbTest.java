package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.service.VideoFeedQueryService;
import com.ssafy.motif.coreapi.global.media.MediaSigningTestSupport;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.video.Visibility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §8.1 챌린지별 참여 영상 피드를 실제 DB에 대고 검증한다 (S15P21D104-140).
 *
 * <p><b>정렬식이 SQL 에 있어 단위 테스트가 닿지 않는다.</b> 점수를 Java 로 빼지 않은
 * 대신({@code ParticipationVideoRepository#FEED_SCORE} javadoc) 순서를 여기서 고정한다 —
 * 여기가 공식의 유일한 회귀 방지선이다.
 *
 * <p><b>이 목록은 챌린지 스코프다.</b> 테스트가 만든 챌린지에만 영상을 달므로 다른
 * 세션이 남긴 데이터가 섞이지 않는다(전역 목록인 §7.1 피드와 다른 점이다).
 *
 * <p><b>커서를 생략한 호출을 반드시 테스트한다.</b> 커서 유무를 한 쿼리에
 * {@code :cursor is null or …} 로 합치면 타입 없는 {@code null} 을 Postgres 가 추론하다
 * 운영에서 500 이 난다 — 이 프로젝트가 그 함정을 두 번 밟았다(389·385).
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ChallengeVideoFeedLocalDbTest {

    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        MediaSigningTestSupport.register(registry);
    }

    @Autowired
    private VideoFeedQueryService videoFeedQueryService;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer me;
    private Integer other;
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        me = data.newUser();
        other = data.newUser();
        artistSeq = data.newArtist();
        challengeSeq = data.newChallenge(artistSeq, data.newSong(me), false);
    }

    // --- 정렬 ---------------------------------------------------------------

    @Test
    @DisplayName("아티스트가 반응한 영상이 조회수가 훨씬 많은 영상보다 위다 — 강가중")
    void artistReactionOutranksViewCount() {
        Long 조회수만 = video().owner(other).views(1_000_000L).insert();
        Long 아티스트좋아요 = video().owner(other).views(1L).artistLike().insert();

        List<Long> feed = seqs(feed(null, 10));

        assertThat(feed.indexOf(아티스트좋아요)).isLessThan(feed.indexOf(조회수만));
    }

    @Test
    @DisplayName("아티스트 반응이 같으면 조회수 순이다 — 좋아요·댓글 수는 순서에 안 넣는다")
    void viewCountBreaksTheTie() {
        Long 적음 = video().owner(other).views(10L).likes(999).comments(999).insert();
        Long 많음 = video().owner(other).views(500L).insert();

        List<Long> feed = seqs(feed(null, 10));

        assertThat(feed.indexOf(많음)).isLessThan(feed.indexOf(적음));
    }

    @Test
    @DisplayName("좋아요와 댓글 반응을 둘 다 받은 영상이 맨 위다")
    void bothArtistReactionsRankHighest() {
        Long 좋아요만 = video().owner(other).views(100L).artistLike().insert();
        Long 둘다 = video().owner(other).views(1L).artistLike().artistComment().insert();
        Long 없음 = video().owner(other).views(9_999L).insert();

        assertThat(seqs(feed(null, 10))).containsExactly(둘다, 좋아요만, 없음);
    }

    // --- 제외 ---------------------------------------------------------------

    @Test
    @DisplayName("타인의 PRIVATE 은 빠지고 내 PRIVATE 은 보인다")
    void othersPrivateIsHiddenButMineIsNot() {
        Long 남의비공개 = video().owner(other).privateVisibility().insert();
        Long 내비공개 = video().owner(me).privateVisibility().insert();

        List<Long> feed = seqs(feed(null, 10));

        assertThat(feed).contains(내비공개).doesNotContain(남의비공개);
    }

    @Test
    @DisplayName("삭제된 영상은 빠진다")
    void deletedVideoIsHidden() {
        Long 삭제됨 = video().owner(other).deleted().insert();

        assertThat(seqs(feed(null, 10))).doesNotContain(삭제됨);
    }

    @Test
    @DisplayName("삭제된 사용자의 영상은 빠진다 — §1.8 공통 비노출")
    void videoOfDeletedUserIsHidden() {
        Integer 탈퇴자 = data.newUser();
        Long 탈퇴자영상 = video().owner(탈퇴자).insert();
        execute("update motif_core.users set is_deleted = true where user_seq = ?1", 탈퇴자);

        assertThat(seqs(feed(null, 10))).doesNotContain(탈퇴자영상);
    }

    @Test
    @DisplayName("관심 없음 한 영상은 내 피드에서만 빠진다 (§8.6)")
    void hiddenVideoIsExcludedForThatUserOnly() {
        Long 숨긴것 = video().owner(other).insert();
        execute("insert into motif_core.video_hides (user_seq, video_seq) values (?1, ?2)",
                me, 숨긴것);

        assertThat(seqs(feed(null, 10))).doesNotContain(숨긴것);
        assertThat(seqs(videoFeedQueryService.feed(other, challengeSeq, null, 10)))
                .contains(숨긴것);
    }

    // --- 항목 ---------------------------------------------------------------

    @Test
    @DisplayName("카드 JPQL 이 돈다 — 작성자·좋아요·본인 여부가 채워진다")
    void cardQueryRuns() {
        Long 남의것 = video().owner(other).views(300L).likes(12).comments(3).insert();
        execute("insert into motif_core.video_likes (video_seq, user_seq) values (?1, ?2)",
                남의것, me);

        VideoItem item = feed(null, 10).list().stream()
                .filter(v -> v.videoSeq().equals(남의것))
                .findFirst()
                .orElseThrow();

        assertThat(item.challengeSeq()).isEqualTo(challengeSeq);
        assertThat(item.author().userSeq()).isEqualTo(other);
        assertThat(item.author().nickname()).isNotBlank();
        assertThat(item.viewCount()).isEqualTo(300L);
        assertThat(item.likeCount()).isEqualTo(12);
        assertThat(item.commentCount()).isEqualTo(3);
        assertThat(item.isLiked()).isTrue();
        assertThat(item.isMine()).isFalse();
        assertThat(item.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(item.durationMs()).isEqualTo(15000);
        assertThat(item.regDttm()).isNotNull();
    }

    @Test
    @DisplayName("보호 등급 영상만 서명이 붙는다 — 판정은 키 프리픽스다 (§1.7 · ADR-0012)")
    void videoUrlFollowsTheKeyPrefix() {
        Long 공개 = video().owner(me).insert();
        Long 보호 = video().owner(me).privateVisibility().prefix("private/challenge/").insert();

        List<VideoItem> list = feed(null, 10).list();

        // 조각까지 통하는 프리픽스 서명이라 Policy 다 — 단일 URL 서명(Expires)이 아니다
        assertThat(item(list, 보호).videoUrl())
                .startsWith(MediaSigningTestSupport.CDN + "/private/challenge/")
                .contains("/master.m3u8?")
                .contains("Policy=", "Signature=", "Key-Pair-Id=");
        assertThat(item(list, 보호).isMine()).isTrue();

        // 공개 사본은 서명이 없다. 붙이면 CDN 캐시를 사용자마다 쪼개는 셈이다
        assertThat(item(list, 공개).videoUrl())
                .startsWith(MediaSigningTestSupport.CDN + "/public/challenge/")
                .endsWith("/master.m3u8");
    }

    // --- 커서 ---------------------------------------------------------------

    @Test
    @DisplayName("커서로 이어 받으면 겹치지도 빠지지도 않는다")
    void cursorWalksTheFeedWithoutGapsOrDuplicates() {
        for (int i = 1; i <= 5; i++) {
            video().owner(other).views(i * 100L).insert();
        }

        CursorResponse<VideoItem> first = feed(null, 2);
        assertThat(first.list()).hasSize(2);
        assertThat(first.hasNext()).isTrue();

        CursorResponse<VideoItem> second = feed(first.nextCursor(), 2);
        assertThat(second.list()).hasSize(2);

        CursorResponse<VideoItem> third = feed(second.nextCursor(), 2);
        assertThat(third.list()).hasSize(1);
        assertThat(third.hasNext()).isFalse();
        assertThat(third.nextCursor()).isNull();

        List<Long> walked = Stream.of(first, second, third)
                .flatMap(page -> seqs(page).stream())
                .toList();
        assertThat(walked).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("점수가 같아도 페이지가 겹치지 않는다 — videoSeq 보조 키")
    void tiedScoresDoNotRepeatAcrossPages() {
        for (int i = 0; i < 4; i++) {
            video().owner(other).views(0L).insert();          // 전부 0점
        }

        CursorResponse<VideoItem> first = feed(null, 2);
        CursorResponse<VideoItem> second = feed(first.nextCursor(), 2);

        assertThat(seqs(second)).doesNotContainAnyElementsOf(seqs(first));
    }

    @Test
    @DisplayName("영상이 없으면 빈 목록 200 이다 — 404 가 아니다")
    void emptyFeedIsNotAnError() {
        CursorResponse<VideoItem> response = feed(null, 10);

        assertThat(response.list()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("size 는 1~100 으로 잘린다 — §1.9 공통 상한")
    void sizeIsClamped() {
        video().owner(other).views(10L).insert();
        video().owner(other).views(20L).insert();

        assertThat(feed(null, 0).list()).hasSize(1);          // 0·음수는 1건으로
        assertThat(feed(null, 1_000).list()).hasSize(2);      // 상한을 넘겨도 터지지 않는다
    }

    // --- 실패 ---------------------------------------------------------------

    @Test
    @DisplayName("없는 챌린지는 404 다")
    void unknownChallengeIs404() {
        assertThatThrownBy(() -> videoFeedQueryService.feed(me, 999_999_999, null, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 챌린지도 404 다 — 빈 목록으로 답하지 않는다")
    void deletedChallengeIs404() {
        Integer 삭제된챌린지 = data.newChallenge(artistSeq, data.newSong(me), true);

        assertThatThrownBy(() -> videoFeedQueryService.feed(me, 삭제된챌린지, null, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("망가진 커서는 400 이다")
    void brokenCursorIs400() {
        assertThatThrownBy(() -> feed("!!!not-base64!!!", 10))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 목록의 커서를 넣어도 형식이 같으면 통과한다 — 구간만 다르다")
    void foreignScoreShapedCursorIsAccepted() {
        video().owner(other).views(10L).insert();

        CursorResponse<VideoItem> response = feed(Cursors.encodeScore(Long.MAX_VALUE, 0L), 10);

        assertThat(response.list()).isNotEmpty();
    }

    // --- 도우미 -------------------------------------------------------------

    private CursorResponse<VideoItem> feed(String cursor, int size) {
        return videoFeedQueryService.feed(me, challengeSeq, cursor, size);
    }

    private static List<Long> seqs(CursorResponse<VideoItem> response) {
        return response.list().stream().map(VideoItem::videoSeq).toList();
    }

    private static VideoItem item(List<VideoItem> list, Long videoSeq) {
        return list.stream()
                .filter(v -> v.videoSeq().equals(videoSeq))
                .findFirst()
                .orElseThrow(() -> new AssertionError("피드에 없다: " + videoSeq));
    }

    // --- 픽스처 -------------------------------------------------------------

    private VideoTestData.VideoFixture video() {
        return data.video(challengeSeq, artistSeq);
    }

    private void execute(String sql, Object... params) {
        data.execute(sql, params);
    }
}

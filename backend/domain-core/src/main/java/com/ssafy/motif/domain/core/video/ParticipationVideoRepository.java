package com.ssafy.motif.domain.core.video;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipationVideoRepository extends JpaRepository<ParticipationVideo, Long> {

    /**
     * 내 참여 영상 최신순 — 마이페이지 프리뷰(§4.1)와 전체 그리드(§4.8)가 함께 쓴다.
     *
     * <p><b>{@code PRIVATE} 도 포함한다.</b> 본인 화면이라 둘 다 보여 주고 항목에
     * {@code visibility} 를 실어 클라이언트가 자물쇠를 그린다.
     *
     * <p><b>삭제된 챌린지에 속한 영상은 뺀다</b>(§1.8 공통 비노출) — 영상 자체가
     * 살아 있어도 그렇다. 연관 매핑이 없어 {@code exists} 로 건다: 참여 영상은 챌린지를
     * 통해 아무것도 읽지 않으므로 {@code @ManyToOne} 을 두면 쓰지도 않을 조인이
     * 따라다닌다.
     *
     * <p>{@code reg_dttm} 이 아니라 PK 역순으로 정렬한다 — IDENTITY 라 단조 증가해
     * 최신순과 결과가 같은데, 같은 초에 올라온 영상들이 호출마다 순서를 바꿔 가며
     * 나오는 일이 없다({@code ChallengeRepository} 와 같은 근거).
     *
     * <p><b>건수는 {@link Pageable} 로 부르는 쪽이 정한다.</b> §4.1 은 9건(3×3 화면
     * 사정), §4.8 은 {@code Pageable#unpaged()} 로 전량이다 — 둘 다 같은 필터를 써야
     * 하므로 쿼리를 나누지 않는다. 나누면 §1.8 비노출이 한쪽에서만 고쳐질 수 있다.
     */
    @Query("""
            select v from ParticipationVideo v
            where v.userSeq = :userSeq
              and v.deleted = false
              and exists (select 1 from Challenge c
                           where c.challengeSeq = v.challengeSeq and c.deleted = false)
            order by v.videoSeq desc
            """)
    List<ParticipationVideo> findMine(@Param("userSeq") Integer userSeq, Pageable pageable);

    /**
     * 구독 카드의 {@code challengeCount} (API 명세 §4.7) — 아티스트별 <b>내</b> 영상 수.
     *
     * <p>이름이 challenge 지만 세는 것은 <b>참여 영상</b>이다(명세 §4.7 표).
     *
     * <p><b>{@link #findMine} 과 같은 필터를 쓴다</b> — 삭제된 챌린지에 속한 영상까지
     * 뺀다. 명세는 "내 비삭제 참여 영상 수"라고만 적었지만, 여기서 §1.8 을 빼면
     * <b>§4.8 그리드에는 안 보이는 영상이 이 숫자에는 잡힌다</b>. "5개라는데 4개만
     * 보인다"가 되고 사용자는 어느 쪽이 맞는지 알 수 없다.
     *
     * <p>그래서 명세가 언급한 {@code idx_pv_user_artist} 만으로는 끝나지 않고 챌린지
     * 확인이 붙는다. 목록과 숫자가 어긋나는 것보다 낫다고 본다.
     *
     * <p>아티스트 목록 전체를 한 번에 센다 — 카드마다 부르면 그대로 N+1 이다
     * ({@code MotifAssetRepository.countByArtists} 와 같은 방식).
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(v.artistSeq, count(v))
            from ParticipationVideo v
            where v.userSeq = :userSeq
              and v.artistSeq in :artistSeqs
              and v.deleted = false
              and exists (select 1 from Challenge c
                           where c.challengeSeq = v.challengeSeq and c.deleted = false)
            group by v.artistSeq
            """)
    List<ArtistCount> countMineByArtists(@Param("userSeq") Integer userSeq,
                                         @Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * 보호 등급 인가 — 이 잡이 게시됐는지, 됐다면 공개 범위가 무엇인지 (API 명세 §1.7).
     *
     * <p><b>삭제분도 가져온다.</b> 여기서 {@code is_deleted=false} 를 걸면 삭제된 영상이
     * "게시된 적 없음"과 같아져 <b>소유자 전용</b> 분기로 떨어지는데, 그쪽이 더 느슨하다
     * — 삭제한 PUBLIC 영상의 재생목록을 소유자에게 계속 내주게 된다. 삭제 판단은
     * 호출자가 따로 한다.
     */
    Optional<ParticipationVideo> findByJobSeq(Long jobSeq);

    /**
     * HOT 랭킹 배치의 재료 — 스냅샷 후보 전량 (API 명세 §6.1).
     *
     * <p>계산은 {@code VideoRankingScore}(순수)가 하고 여기는 재료만 꺼낸다.
     *
     * <p><b>모집단이 곧 노출 범위다.</b> §6.1 은 스냅샷 순서를 그대로 내보내므로
     * 여기서 빠진 영상은 HOT 에 아예 오르지 않는다. 그래서 §1.8 공통 비노출을
     * 여기에 건다 — 삭제된 챌린지에 속한 영상과 삭제 사용자의 영상을 뺀다.
     * {@link #findMine} 과 같은 이유로 {@code exists} 를 쓴다(연관 매핑을 두면 쓰지도
     * 않을 조인이 따라다닌다).
     *
     * <p><b>{@code PRIVATE} 은 후보가 아니다.</b> 인기 목록은 남의 영상을 보여 주는
     * 자리고 {@code PRIVATE} 은 본인 외에 존재 자체를 숨긴다(§1.8).
     *
     * <p><b>공개 사본 여부는 여기서 보지 않는다.</b> 키 프리픽스로 등급을 판정하는
     * 지점은 {@code MediaKeys} 하나여야 하므로({@code 'private/%'} 리터럴을 SQL 에
     * 넣으면 규약이 두 곳으로 갈라진다) {@code videoKey} 를 실어 보내고 배치가 건다.
     *
     * <p>집계 없이 전량을 준다 — 감쇠가 영상 하나하나에 걸려야 의미가 있다. 5분에
     * 한 번이라 전량 스캔이 부담되지 않는다({@code ChallengeRepository.findRankingInputs}
     * 와 같은 판단).
     */
    @Query("""
            select new com.ssafy.motif.domain.core.video.VideoScoreRow(
                       v.videoSeq, v.viewCount, v.regDttm, v.videoKey)
            from ParticipationVideo v
            where v.deleted = false
              and v.visibility = com.ssafy.motif.domain.core.video.Visibility.PUBLIC
              and exists (select 1 from Challenge c
                           where c.challengeSeq = v.challengeSeq and c.deleted = false)
              and exists (select 1 from User u
                           where u.userSeq = v.userSeq and u.deleted = false)
            """)
    List<VideoScoreRow> findRankingInputs();

    /**
     * HOT 카드 배치 조회 — 스냅샷이 준 {@code videoSeq} 묶음을 한 번에 읽는다
     * (API 명세 §6.1).
     *
     * <p><b>비노출 조건을 한 번 더 건다.</b> {@link #findRankingInputs} 가 이미 걸렀지만
     * 스냅샷은 5분마다 갱신되므로 그 사이에 삭제·비공개로 바뀐 영상이 남아 있다.
     * 명세 §1.8 은 조회 API <b>전부</b>에 적용되는 규칙이라 읽는 쪽에서 막는다.
     *
     * <p>그래서 결과가 요청한 {@code videoSeqs} 보다 적을 수 있고, 응답의 {@code rank}
     * 에 건너뛴 번호가 생긴다. {@code rank} 는 스냅샷 순위라서 그것이 맞는 값이다 —
     * 번호를 다시 매기면 같은 커서로 이어 받은 페이지와 어긋난다.
     *
     * <p>{@code in} 은 순서를 보장하지 않는다 — 호출부가 스냅샷 순서로 다시 세운다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.video.HotVideoRow(
                       v.videoSeq, v.videoKey, v.thumbnailKey, v.durationMs, v.viewCount,
                       s.title, s.singerName, u.userSeq, u.nickname)
            from ParticipationVideo v
              join Challenge c on c.challengeSeq = v.challengeSeq
              join Song s on s.songSeq = c.songSeq
              join User u on u.userSeq = v.userSeq
            where v.videoSeq in :videoSeqs
              and v.deleted = false
              and v.visibility = com.ssafy.motif.domain.core.video.Visibility.PUBLIC
              and c.deleted = false
              and u.deleted = false
            """)
    List<HotVideoRow> findHotCards(@Param("videoSeqs") Collection<Long> videoSeqs);

    /**
     * 챌린지별 피드의 추천 점수 (API 명세 §8.1) — <b>공식이 사는 유일한 자리</b>.
     *
     * <pre>
     * score = (has_artist_like + has_artist_comment) × 10¹² + view_count
     * </pre>
     *
     * <p><b>아티스트 반응이 사실상 다른 층이다.</b> §8.1 은 "아티스트가 반응한 영상을
     * 위로"를 요구하는데, 가중치를 조회수와 같은 자릿수로 주면 조회수가 충분히 큰
     * 영상이 그 위로 올라가 요구가 조용히 무너진다. {@code view_count} 가 BIGINT 라
     * 10¹² 는 안전한 경계다 — 실제 조회수가 1조에 닿는 일이 없고, 둘 다 반응한 영상
     * (2×10¹²)까지 더해도 BIGINT 상한에서 멀다.
     *
     * <p><b>좋아요·댓글 수는 점수에 넣지 않는다</b>(§6.1 {@code VideoRankingScore} 와
     * 같은 기본안). 응답에는 그대로 나가지만 순서를 정하지는 않는다.
     *
     * <p><b>왜 SQL 인가.</b> §5.1·§6.1 의 점수는 시간 감쇠가 있어 {@code now} 를 주입해야
     * 했고, 그래서 순수 Java 클래스로 뺐다. 여기 점수는 <b>저장된 칼럼만의 함수</b>라
     * 시간축이 없고, Java 로 빼면 정렬을 위해 <b>그 챌린지의 후보 전량</b>을 매 요청마다
     * 읽어야 한다(§6.1 은 5분에 한 번 도는 배치라 전량 스캔이 허용됐다). 대신 순서
     * 검증은 local-db 테스트가 맡는다.
     *
     * <p>상수로 둔 것은 {@code where}(커서 비교)와 {@code order by} 가 <b>같은 식</b>을
     * 써야 하기 때문이다. 문자열을 두 번 적으면 한쪽만 고쳤을 때 페이지 경계에서만
     * 항목이 어긋난다 — 예외도 로그도 남지 않는 종류다.
     */
    String FEED_SCORE = "((case when v.has_artist_like then 1 else 0 end"
            + " + case when v.has_artist_comment then 1 else 0 end) * 1000000000000"
            + " + v.view_count)";

    /**
     * 피드 후보 — §1.8 비노출과 §8.6 관심 없음을 한 번에 건다.
     *
     * <p><b>{@code PRIVATE} 은 본인 것만 남긴다.</b> §8.1 이 빼라고 적은 것은 "타인의
     * {@code PRIVATE}" 이고, 내 비공개 영상은 내 피드에 보인다(§4.8 그리드와 같은 취급).
     *
     * <p>삭제된 챌린지 확인이 여기 없는 것은 의도다 — 호출부가 챌린지를 먼저 읽어
     * 404 로 끊는다. 여기서 또 걸면 "빈 목록 200" 과 404 가 같은 상황에서 갈린다.
     */
    String FEED_FROM = " from motif_core.participation_videos v"
            + " join motif_core.users u on u.user_seq = v.user_seq and u.is_deleted = false"
            + " where v.challenge_seq = :challengeSeq"
            + "   and v.is_deleted = false"
            + "   and (v.visibility = 'PUBLIC' or v.user_seq = :userSeq)"
            + "   and not exists (select 1 from motif_core.video_hides h"
            + "                    where h.user_seq = :userSeq and h.video_seq = v.video_seq)";

    /**
     * 정렬·절단. {@code video_seq} 보조 키가 없으면 <b>점수가 같은 영상들이 호출마다
     * 자리를 바꿔</b> 커서 페이지에 중복·누락이 생긴다({@code HomeActivityRepository} 와
     * 같은 근거). 점수 0인 영상이 흔하므로 실제로 일어난다.
     *
     * <p>별칭에 따옴표를 두른 이유는 Postgres 가 따옴표 없는 식별자를 소문자로 내리기
     * 때문이다 — 그대로 두면 {@code videoSeq} 가 {@code videoseq} 로 와서 투영 게터와
     * 맞지 않는다({@code ActivityKeyRow} 와 같은 함정).
     */
    String FEED_ORDER = " order by \"score\" desc, v.video_seq desc limit :limit";

    /**
     * 피드 첫 페이지의 순서 (API 명세 §8.1).
     *
     * <p>카드 본문을 함께 읽지 않는다 — 순서를 정하는 쿼리와 본문을 읽는 쿼리를
     * 나누면 정렬식이 한 줄로 남고, 본문 조인(작성자·잡)이 정렬 계획에 끼지 않는다
     * (§6.1 스냅샷 → {@link #findHotCards} 와 같은 2단 구조).
     *
     * <p>커서 유무로 메서드를 가른다 — 타입 없는 {@code null} 을 Postgres 가 추론하다
     * 운영에서 500 이 나는 함정을 이 프로젝트가 두 번 밟았다({@code ChallengeRepository}).
     */
    @Query(value = "select v.video_seq as \"videoSeq\", " + FEED_SCORE + " as \"score\""
            + FEED_FROM + FEED_ORDER, nativeQuery = true)
    List<VideoFeedKeyRow> findFeedKeys(@Param("challengeSeq") Integer challengeSeq,
                                       @Param("userSeq") Integer userSeq,
                                       @Param("limit") int limit);

    /**
     * 피드 다음 페이지 — 커서가 가리키는 {@code (점수, videoSeq)} 보다 뒤부터.
     *
     * <p><b>점수는 카운터라 페이지 사이에 변한다.</b> 조회수가 오른 영상이 커서를
     * 앞질러 다시 나오거나(중복) 뒤로 밀려 건너뛸 수(누락) 있다 — keyset 방식이
     * 정렬 키의 불변을 전제하기 때문이고, 순위 목록에서는 피할 수 없다. 대안은 §6.1
     * 처럼 스냅샷을 떠 두는 것인데 챌린지마다 키가 생겨 이 목록에는 과하다.
     * 커서가 opaque 토큰이라(§1.9) 이 선택이 클라이언트 계약을 깨지는 않는다.
     */
    @Query(value = "select v.video_seq as \"videoSeq\", " + FEED_SCORE + " as \"score\""
            + FEED_FROM
            + " and (" + FEED_SCORE + " < :cursorScore"
            + "   or (" + FEED_SCORE + " = :cursorScore and v.video_seq < :cursorVideoSeq))"
            + FEED_ORDER, nativeQuery = true)
    List<VideoFeedKeyRow> findFeedKeysAfter(@Param("challengeSeq") Integer challengeSeq,
                                            @Param("userSeq") Integer userSeq,
                                            @Param("cursorScore") long cursorScore,
                                            @Param("cursorVideoSeq") long cursorVideoSeq,
                                            @Param("limit") int limit);

    /**
     * 피드 카드 배치 조회 (API 명세 §8.1) — 순서 쿼리가 고른 {@code videoSeq} 묶음.
     *
     * <p>{@link #findFeedKeys} 가 이미 비노출을 걸렀고 같은 트랜잭션 안이라 조건을 다시
     * 걸지 않는다 — §6.1 {@link #findHotCards} 가 한 번 더 거는 것은 그쪽 입력이
     * <b>5분 전 스냅샷</b>이라서다. 여기 입력은 방금 읽은 것이다.
     *
     * <p>작성자와 잡을 함께 조인한다. {@code jobUuid} 는 보호 등급 영상의 재생목록
     * 주소를 만드는 재료다(§1.7) — 보호 등급만 골라 따로 읽으면 페이지당 왕복이
     * 하나 더 생긴다.
     *
     * <p>{@code in} 은 순서를 보장하지 않는다 — 호출부가 순서 쿼리의 결과로 다시 세운다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.video.VideoFeedRow(
                       v.videoSeq, v.challengeSeq, v.userSeq, v.description,
                       v.videoKey, v.thumbnailKey, v.durationMs,
                       v.viewCount, v.likeCount, v.commentCount,
                       v.hasArtistLike, v.hasArtistComment,
                       v.commentEnabled, v.visibility, v.regDttm,
                       u.nickname, u.profileImageKey, j.jobUuid)
            from ParticipationVideo v
              join User u on u.userSeq = v.userSeq
              join CompositeJob j on j.jobSeq = v.jobSeq
            where v.videoSeq in :videoSeqs
            """)
    List<VideoFeedRow> findFeedCards(@Param("videoSeqs") Collection<Long> videoSeqs);

    /**
     * 참여 영상 단건 (API 명세 §8.2) — <b>§1.8 비노출을 쿼리에서 건다</b>.
     *
     * <p>삭제 영상 · 삭제 사용자의 콘텐츠 · 삭제된 챌린지 소속을 여기서 떨어뜨려
     * "없음" 으로 만든다. 셋을 서비스에서 가르면 각각 다른 코드로 답할 길이 열리는데,
     * 명세 §8.2 실패표는 <b>전부 같은 404</b> 다.
     *
     * <p><b>타인의 {@code PRIVATE} 은 여기서 거르지 않는다.</b> 그 판정에는 요청자가
     * 필요한데, 쿼리에 {@code userSeq} 를 넣으면 이 메서드가 "보이는가" 까지 떠맡아
     * §8.9·§8.10(본인만 수정·삭제)이 쓸 수 없게 된다 — 그쪽은 남의 영상도 찾아낸 뒤
     * 403 을 내야 한다. 공개 범위 판단은 호출부 몫이다.
     *
     * <p>투영은 {@link #findFeedCards} 와 같은 {@link VideoFeedRow} 다 — 명세가 §8.1
     * 항목과 §8.2 응답을 같은 형태로 못박았으므로 읽어 오는 칸도 같아야 한다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.video.VideoFeedRow(
                       v.videoSeq, v.challengeSeq, v.userSeq, v.description,
                       v.videoKey, v.thumbnailKey, v.durationMs,
                       v.viewCount, v.likeCount, v.commentCount,
                       v.hasArtistLike, v.hasArtistComment,
                       v.commentEnabled, v.visibility, v.regDttm,
                       u.nickname, u.profileImageKey, j.jobUuid)
            from ParticipationVideo v
              join User u on u.userSeq = v.userSeq
              join CompositeJob j on j.jobSeq = v.jobSeq
              join Challenge c on c.challengeSeq = v.challengeSeq
            where v.videoSeq = :videoSeq
              and v.deleted = false
              and u.deleted = false
              and c.deleted = false
            """)
    Optional<VideoFeedRow> findDetailCard(@Param("videoSeq") Long videoSeq);

    /**
     * 한 페이지 분 {@code isLiked} 를 한 번에 (API 명세 §8.1).
     *
     * <p><b>네이티브다.</b> {@code video_likes} 는 엔티티가 없다 — 여기서 필요한 것이
     * {@code video_seq} 하나뿐이고, 쓰기(§8.3 토글)를 담당하는 티켓이 따로 있어 지금
     * 엔티티를 만들면 같은 파일을 양쪽에서 만들게 된다
     * ({@code ChallengeRepository.findLikedChallengeSeqs} 와 같은 판단).
     *
     * <p>항목마다 물으면 20행짜리 페이지에 20번을 더 왕복한다. 좋아요한 seq 만
     * 돌려주고 호출부가 {@code Set} 으로 대조한다.
     */
    @Query(value = """
            select video_seq from motif_core.video_likes
             where user_seq = :userSeq and video_seq in (:videoSeqs)
            """, nativeQuery = true)
    List<Long> findLikedVideoSeqs(@Param("userSeq") Integer userSeq,
                                  @Param("videoSeqs") Collection<Long> videoSeqs);

    /**
     * 관심 없음 (API 명세 §8.6) — 이후 §8.1 피드에서 그 영상을 뺀다.
     *
     * <p><b>네이티브다.</b> {@code video_hides} 는 엔티티가 없다 — §8.1 이 같은 테이블을
     * {@code not exists} 로 읽는 것이 전부이고, 행이 곧 상태라 담을 칸도 없다
     * ({@code findLikedVideoSeqs} 와 같은 판단).
     *
     * <p><b>반환값을 호출부가 보지 않는다.</b> {@code on conflict do nothing} 을 쓰는 것은
     * 중복을 가르기 위해서가 아니라 <b>가르지 않기 위해서</b>다 — §8.6 은 멱등이고, 두 번
     * 눌러도 사용자가 원하는 상태는 같다. 같은 구문을 쓰는 §8.7 신고가 영향 행 수로 409 를
     * 내는 것과 정반대이므로, 그쪽 코드를 보고 여기에 409 를 더하지 않는다.
     *
     * <p>{@code reg_dttm} 을 넣지 않는 것은 DDL 에 {@code DEFAULT CURRENT_TIMESTAMP} 가
     * 있기 때문이다(0005-03).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.video_hides (user_seq, video_seq)
            values (:userSeq, :videoSeq)
            on conflict do nothing
            """, nativeQuery = true)
    void insertHideIfAbsent(@Param("userSeq") Integer userSeq, @Param("videoSeq") Long videoSeq);
}

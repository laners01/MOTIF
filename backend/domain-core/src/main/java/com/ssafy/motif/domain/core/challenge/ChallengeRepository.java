package com.ssafy.motif.domain.core.challenge;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Integer> {

    /**
     * 참여 수 증가 — 참여 영상이 게시될 때 (API 명세 §8.8 · DB §26).
     *
     * <p>UPDATE 한 문장이다 — 엔티티를 읽어 +1 하고 저장하면 동시 게시에 유실된다
     * ({@link #decrementParticipantCount} 와 같은 이유).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Challenge c
               set c.participantCount = c.participantCount + 1
             where c.challengeSeq = :challengeSeq
            """)
    int incrementParticipantCount(@Param("challengeSeq") Integer challengeSeq);

    /**
     * 참여 수 감소 — 참여 영상이 처음 삭제될 때 (어드민 명세 §3.13.3 · DB §26).
     *
     * <p><b>0 아래로 내려가지 않는다.</b> {@code participant_count > 0} 조건이 바닥을
     * 막는다. 신고 인용이 멱등이라 같은 영상에 두 번 불릴 수 있는데, 그때 음수가 되면
     * 챌린지 목록의 참여 수가 영영 틀어진다.
     *
     * <p>UPDATE 한 문장이다 — 엔티티를 읽어 −1 하고 저장하면 동시 삭제에 유실된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Challenge c
               set c.participantCount = c.participantCount - 1
             where c.challengeSeq = :challengeSeq and c.participantCount > 0
            """)
    int decrementParticipantCount(@Param("challengeSeq") Integer challengeSeq);

    /**
     * 댓글 수 증감 (API 명세 §9.3·§9.4 · DB §26) — <b>거울 카운터</b>.
     *
     * <p>{@code delta} 는 작성에서 {@code +1}, 삭제에서 <b>실제로 삭제된 행 수만큼</b>
     * 음수다(루트를 지우면 직계 답글이 함께 지워져 1보다 크다).
     *
     * <p>UPDATE 한 문장이다 — 엔티티를 읽어 더하고 저장하면 동시 작성에서 유실된다.
     * 바닥 조건({@code commentCount + delta >= 0})이 음수를 막는다
     * ({@link #decrementParticipantCount} 와 같은 장치다).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Challenge c
               set c.commentCount = c.commentCount + :delta
             where c.challengeSeq = :challengeSeq and c.commentCount + :delta >= 0
            """)
    int addCommentCount(@Param("challengeSeq") Integer challengeSeq, @Param("delta") int delta);

    /**
     * 살아 있는 챌린지인가 (§7.3) — 엔티티를 읽지 않는다.
     *
     * <p>{@link #findByChallengeSeqAndDeletedFalse} 가 아니라 {@code exists} 인 이유는
     * §10.2 와 같다: 좋아요는 204 라 돌려줄 값이 없고, 뒤따르는 증감이 벌크 UPDATE 라
     * 영속성 컨텍스트에 실은 행은 곧 버려진다.
     */
    boolean existsByChallengeSeqAndDeletedFalse(Integer challengeSeq);

    /**
     * 좋아요 (§7.3) — 이미 누른 상태면 <b>0을 돌려준다</b>.
     *
     * <p>멱등이 복합 PK 에서 나온다({@code NoticeRepository.insertLikeIfAbsent} 와 같은
     * 방식). 네이티브인 이유는 {@code on conflict do nothing} 이 JPQL 에 없어서다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.challenge_likes (challenge_seq, user_seq)
            values (:challengeSeq, :userSeq)
            on conflict do nothing
            """, nativeQuery = true)
    int insertLikeIfAbsent(@Param("challengeSeq") Integer challengeSeq,
                           @Param("userSeq") Integer userSeq);

    /** 좋아요 해제 (§7.3) — <b>하드 삭제</b>다. 누르지 않은 상태면 0을 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.challenge_likes
             where challenge_seq = :challengeSeq and user_seq = :userSeq
            """, nativeQuery = true)
    int deleteLikeIfPresent(@Param("challengeSeq") Integer challengeSeq,
                            @Param("userSeq") Integer userSeq);

    /** 좋아요 수 증감 (§7.3) — 거울 카운터. 규칙은 {@link #addCommentCount} 와 같다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Challenge c
               set c.likeCount = c.likeCount + :delta
             where c.challengeSeq = :challengeSeq and c.likeCount + :delta >= 0
            """)
    int addLikeCount(@Param("challengeSeq") Integer challengeSeq, @Param("delta") int delta);

    /** 아티스트 목록·상세의 {@code challengeCount} (어드민 §3.5.1) — 페이지 전체를 한 번에. */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(c.artistSeq, count(c))
            from Challenge c
            where c.artistSeq in :artistSeqs and c.deleted = false
            group by c.artistSeq
            """)
    List<ArtistCount> countByArtists(@Param("artistSeqs") Collection<Integer> artistSeqs);

    /** 아티스트 상세의 최신 챌린지 5건 (§3.5.3 {@code recentChallenges[]}). */
    List<Challenge> findTop5ByArtistSeqAndDeletedFalseOrderByChallengeSeqDesc(Integer artistSeq);

    /**
     * 아티스트 랭킹 점수의 원자료 (API 명세 §5.1) — 비삭제 챌린지 전량.
     *
     * <p><b>점수를 SQL 에서 계산하지 않는다.</b> 공식이 쿼리 문자열 안에 갇히면 단위
     * 테스트로 묶이지 않는다 — 가중치가 설정으로 빠지는데 정작 그 계산을 검증할 수 없게
     * 된다. 계산은 {@code RankingScore}(순수)가 하고 여기는 재료만 꺼낸다. 게다가 최근·이전
     * 구간 조회수는 DB 에 아예 없다(Redis 버킷 — ADR-0016).
     *
     * <p><b>집계 없이 전량을 준다.</b> 아티스트별로 {@code sum} 해서 주면 챌린지별 최근
     * 조회수를 버킷에서 찾을 키를 잃어버린다. 운영 규모가 수백 건이고 20분에 한 번이라
     * 전량 스캔이 부담되지 않는다.
     *
     * <p><b>{@code view_count > 0} 같은 조건을 걸지 않는다.</b> §6.1 은 모집단을 그렇게
     * 좁혔지만 여기서 같은 것을 하면 <b>온보딩 인기 목록(§3.1)에서 신인이 사라진다</b>
     * — 비삭제 아티스트는 챌린지가 0개여도 0점으로 스냅샷에 들어가야 한다(ADR-0016).
     */
    @Query("""
            select new com.ssafy.motif.domain.core.challenge.ChallengeScoreRow(
                       c.challengeSeq, c.artistSeq, c.viewCount, c.participantCount)
            from Challenge c
            where c.deleted = false
            """)
    List<ChallengeScoreRow> findRankingInputs();

    /**
     * 팬 앱 아티스트 챌린지 그리드 (API 명세 §5.4) — <b>비페이지 전량</b>, 최신순.
     *
     * <p>페이지를 두지 않는 것은 회의 확정(R2)이다. 어드민 목록(§3.7.1)과 달리 필터가
     * 없고 한 아티스트의 챌린지는 운영상 수십 건 규모라, 그리드가 한 번에 받는다.
     *
     * <p><b>참여 수 내림차순, 동수면 최신순</b>이다. 그리드는 "이 아티스트에서 뭐가
     * 도는가" 를 먼저 보여주는 자리라 등록 시각보다 참여가 앞선다.
     *
     * <p>동수 보조 키가 {@code reg_dttm} 이 아니라 PK 역순인 것은 IDENTITY 라 단조
     * 증가해 최신순과 결과가 같으면서, 같은 초에 등록된 챌린지들이 호출마다 순서를
     * 바꿔 가며 나오는 일이 없기 때문이다({@code NoticeRepository.findAdminPage} 와
     * 같은 근거). 참여 0건이 흔해 동수는 실제로 자주 난다.
     *
     * <p>삭제된 챌린지는 뺀다 (§1.8 공통 비노출).
     */
    List<Challenge> findByArtistSeqAndDeletedFalseOrderByParticipantCountDescChallengeSeqDesc(
            Integer artistSeq);

    /**
     * 챌린지 상세의 {@code isLiked} (API 명세 §7.2).
     *
     * <p><b>네이티브다.</b> {@code challenge_likes} 는 아직 엔티티가 없고 여기서 필요한
     * 것은 참·거짓 하나뿐이라, 좋아요 토글(§7.3) 티켓이 소유할 테이블을 미리 매핑하지
     * 않는다 — {@link #countActiveJobs} 와 같은 규칙이다. 그 티켓이 엔티티를 만들면
     * 이 쿼리는 그리로 옮긴다.
     */
    @Query(value = """
            select exists(
                select 1 from motif_core.challenge_likes
                 where challenge_seq = :challengeSeq and user_seq = :userSeq)
            """, nativeQuery = true)
    boolean isLikedBy(@Param("challengeSeq") Integer challengeSeq, @Param("userSeq") Integer userSeq);

    /** 어드민도 삭제된 챌린지는 수정 대상으로 잡지 않는다 — §3.7 실패표 404 "대상 없음·삭제". */
    /**
     * 숏폼 피드 첫 페이지 (API 명세 §7.1) — 최신순 커서.
     *
     * <p><b>커서 유무로 메서드를 가른다.</b> {@code :cursor is null or …} 로 한 쿼리에
     * 합치면 타입 없는 {@code null} 을 Postgres 가 추론하다 운영에서 터진다 —
     * {@code NoticeRepository} 가 같은 이유로 갈라져 있다.
     *
     * <p>정렬 키가 {@code challengeSeq} 하나다. 명세는 "구독 아티스트 + 인기·관심 기반
     * 추천 종합(rank:challenge 활용)" 이라고 적었지만 <b>랭킹이 아직 없다</b> —
     * 최신순으로 내고 추천은 후속이다. 커서가 opaque 토큰이라 정렬 키가 바뀌어도
     * 클라이언트 계약은 깨지지 않는다(§1.9).
     */
    List<Challenge> findByDeletedFalseOrderByChallengeSeqDesc(Pageable pageable);

    /** 피드 다음 페이지 — 커서(마지막 항목의 seq)보다 작은 것부터. */
    List<Challenge> findByDeletedFalseAndChallengeSeqLessThanOrderByChallengeSeqDesc(
            Integer challengeSeq, Pageable pageable);

    /**
     * 한 페이지 분 {@code isLiked} 를 한 번에 (API 명세 §7.1).
     *
     * <p>항목마다 {@link #isLikedBy} 를 부르면 20행짜리 페이지에 20번을 더 왕복한다.
     * 좋아요한 seq 만 돌려주고 호출부가 {@code Set} 으로 대조한다.
     */
    @Query(value = """
            select challenge_seq from motif_core.challenge_likes
             where user_seq = :userSeq and challenge_seq in (:challengeSeqs)
            """, nativeQuery = true)
    List<Integer> findLikedChallengeSeqs(@Param("userSeq") Integer userSeq,
                                         @Param("challengeSeqs") Collection<Integer> challengeSeqs);

    /**
     * 내 활동 카드의 챌린지 본문 배치 조회 (API 명세 §4.9) — 순서 쿼리가 고른 묶음.
     *
     * <p>{@code UserActivityRepository} 가 이미 비노출({@code is_deleted})을 걸렀고 같은
     * 트랜잭션 안이라 조건을 다시 걸지 않는다 — {@code ParticipationVideoRepository
     * .findFeedCards} 와 같은 규칙이다.
     *
     * <p>엔티티를 끌어오지 않는 이유는 카드가 세 칸만 쓰기 때문이다. {@code Challenge}
     * 는 {@code description}(TEXT)·키 두 개까지 실어 오는데 목록에서는 버려진다.
     *
     * <p>{@code in} 은 순서를 보장하지 않는다 — 호출부가 순서 쿼리의 결과로 다시 세운다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.challenge.ChallengeActivityRow(
                       c.challengeSeq, c.thumbnailKey, c.viewCount, c.likeCount)
            from Challenge c
            where c.challengeSeq in :challengeSeqs
            """)
    List<ChallengeActivityRow> findActivityCards(
            @Param("challengeSeqs") Collection<Integer> challengeSeqs);

    Optional<Challenge> findByChallengeSeqAndDeletedFalse(Integer challengeSeq);

    /**
     * 어드민 목록 (어드민 명세 §3.7.1) — 아티스트·곡 필터, offset 페이지.
     *
     * <p><b>필터는 {@code null} 이면 없는 셈 친다.</b> 조합마다 파생 메서드를 만들면
     * 2×2 = 4개가 되고, 필터가 하나 늘 때마다 배가 된다.
     *
     * <p>목록은 <b>삭제된 챌린지를 기본으로 감춘다</b>({@code includeDeleted=false}).
     * 소프트 삭제는 앱 비노출이지 이력 삭제가 아니라, 운영자가 지운 것을 다시 볼
     * 수단은 남겨 둔다.
     *
     * <p>count 쿼리에 조인이 없는 것은 의도다 — {@code artist_seq}·{@code song_seq} 가
     * NOT NULL FK 라 조인이 행을 떨어뜨리지 않는다. 세는 데 3번 조인할 이유가 없다.
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.challenge.ChallengeListRow(
                   c.challengeSeq, c.artistSeq, u.nickname,
                   s.songSeq, s.title, s.singerName,
                   c.thumbnailKey, c.durationMs,
                   (select count(p) from ChallengeParticipant p where p.challengeSeq = c.challengeSeq),
                   (select count(e) from ChallengeParticipant e
                     where e.challengeSeq = c.challengeSeq and e.editedChallengeKey is not null),
                   c.viewCount, c.participantCount, c.likeCount, c.commentCount,
                   c.deleted, c.regDttm)
              from Challenge c, Artist a, User u, Song s
             where a.artistSeq = c.artistSeq and u.userSeq = a.userSeq and s.songSeq = c.songSeq
               and (:artistSeq is null or c.artistSeq = :artistSeq)
               and (:songSeq is null or c.songSeq = :songSeq)
               and (:includeDeleted = true or c.deleted = false)
             order by c.challengeSeq desc
            """,
            countQuery = """
            select count(c) from Challenge c
             where (:artistSeq is null or c.artistSeq = :artistSeq)
               and (:songSeq is null or c.songSeq = :songSeq)
               and (:includeDeleted = true or c.deleted = false)
            """)
    Page<ChallengeListRow> search(@Param("artistSeq") Integer artistSeq,
                                  @Param("songSeq") Integer songSeq,
                                  @Param("includeDeleted") boolean includeDeleted,
                                  Pageable pageable);

    /**
     * 진행 중 합성 잡 수 (§3.7.3 {@code activeJobCount}).
     *
     * <p><b>네이티브다.</b> {@code composite_jobs} 는 아직 엔티티가 없고, 여기서 필요한 것은
     * 숫자 하나뿐이라 CHECK 제약이 얽힌 테이블을 통째로 매핑할 이유가 없다. 잡 도메인
     * 티켓이 엔티티를 만들면 그때 JPQL 로 바꾼다.
     */
    @Query(value = """
            select count(*) from motif_core.composite_jobs
            where challenge_seq = :challengeSeq and status in ('QUEUED', 'PROCESSING')
            """, nativeQuery = true)
    long countActiveJobs(@Param("challengeSeq") Integer challengeSeq);
}

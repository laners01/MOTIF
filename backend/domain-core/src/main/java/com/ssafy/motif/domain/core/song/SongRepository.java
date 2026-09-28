package com.ssafy.motif.domain.core.song;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SongRepository extends JpaRepository<Song, Integer> {

    /**
     * 곡명·가수명 부분 일치 검색 (어드민 명세 §3.6.1).
     *
     * <p><b>{@code q} 에 {@code null} 을 넘기지 않는다.</b> 검색어가 없으면 호출부가 빈
     * 문자열로 정규화해 넘기고, 그러면 {@code like '%%'} 가 되어 전체 조회가 된다 —
     * 검색용·전체용 메서드를 따로 두지 않으면서 분기도 없앤 방식이다.
     *
     * <p>{@code :q is null} 분기를 두지 않는 이유(실측): 타입 없는 {@code null} 파라미터를
     * Postgres 가 {@code bytea} 로 추론해 {@code function lower(bytea) does not exist} 로
     * 쿼리가 죽는다. 정규화를 호출부에 두면 이 함정 자체가 사라진다.
     *
     * <p><b>trgm 인덱스가 없다.</b> 명세가 "규모 소"라고 못박은 테이블이라 full scan +
     * ILIKE 로 충분하다 — 곡 수가 수만 건이 되면 그때 인덱스를 단다.
     * {@code lower(...) like lower(...)} 는 Postgres {@code ILIKE} 와 같은 뜻이고
     * JPQL 로 쓸 수 있다.
     */
    @Query("""
            select s from Song s
            where lower(s.title) like lower(concat('%', :q, '%'))
               or lower(s.singerName) like lower(concat('%', :q, '%'))
            """)
    Page<Song> search(@Param("q") String q, Pageable pageable);

    /**
     * 곡별 챌린지 수 (§3.6.1 항목의 {@code challengeCount}).
     *
     * <p><b>삭제되지 않은 챌린지만 센다.</b> 이 숫자의 용도는 "이 곡이 지금 쓰이고 있나"
     * 이고, 소프트 삭제된 챌린지는 앱에 노출되지 않으므로 쓰이는 중이 아니다.
     *
     * <p>네이티브인 이유는 {@code EditedChallengeRepository.countJobRefs} 와 같다 —
     * 다른 애그리거트를 숫자 하나 때문에 조인 매핑하지 않는다. 챌린지가 없는 곡은
     * group by 결과에 행이 없으므로 호출부가 기본값 0 을 쓴다.
     */
    @Query(value = """
            select c.song_seq as songSeq, count(*) as challengeCount
            from motif_core.challenges c
            where c.song_seq in (:songSeqs) and c.is_deleted = false
            group by c.song_seq
            """, nativeQuery = true)
    List<ChallengeCount> countChallenges(@Param("songSeqs") Collection<Integer> songSeqs);

    interface ChallengeCount {
        Integer getSongSeq();

        long getChallengeCount();
    }

    /**
     * 아티스트별 대표곡 (API 명세 §5.1 {@code challengeSeq}·{@code songTitle}·{@code singerName}).
     *
     * <p>대표곡은 <b>가장 최근에 등록된 비삭제 챌린지의 곡</b>이다. {@code distinct on} 이
     * 아티스트마다 {@code challenge_seq} 가 가장 큰 한 행만 남긴다(IDENTITY 라 최신순과 같다).
     * 챌린지가 없는 아티스트는 결과에 행이 없고 호출부가 {@code null} 로 둔다.
     */
    @Query(value = """
            select distinct on (c.artist_seq)
                   c.artist_seq as artistSeq, c.challenge_seq as challengeSeq,
                   s.title as songTitle, s.singer_name as singerName
            from motif_core.challenges c
              join motif_core.songs s on s.song_seq = c.song_seq
            where c.artist_seq in (:artistSeqs) and c.is_deleted = false
            order by c.artist_seq, c.challenge_seq desc
            """, nativeQuery = true)
    List<ArtistSong> findLatestSongs(@Param("artistSeqs") Collection<Integer> artistSeqs);

    interface ArtistSong {
        Integer getArtistSeq();

        Integer getChallengeSeq();

        String getSongTitle();

        String getSingerName();
    }
}

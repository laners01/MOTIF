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
     * <p>{@code q} 가 없으면 전체다 — {@code :q is null} 분기를 쿼리 안에 둬서 호출부가
     * 검색용·전체용 메서드를 따로 갖지 않게 한다.
     *
     * <p><b>trgm 인덱스가 없다.</b> 명세가 "규모 소"라고 못박은 테이블이라 full scan +
     * ILIKE 로 충분하다 — 곡 수가 수만 건이 되면 그때 인덱스를 단다.
     * {@code lower(...) like lower(...)} 는 Postgres {@code ILIKE} 와 같은 뜻이고
     * JPQL 로 쓸 수 있다.
     */
    @Query("""
            select s from Song s
            where :q is null
               or lower(s.title) like lower(concat('%', :q, '%'))
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
}

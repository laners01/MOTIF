package com.ssafy.motif.domain.core.artist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {

    /** 삭제된 아티스트는 없는 것으로 본다 — 상세·수정 대상 조회(§3.5.3·§3.5.4). */
    Optional<Artist> findByArtistSeqAndDeletedFalse(Integer artistSeq);

    /**
     * 상세 한 줄 (§3.5.3) — 목록과 같은 조인·같은 투영이다.
     *
     * <p>삭제된 아티스트도 준다. 목록에서 {@code includeDeleted} 로 찾아 들어간 화면이
     * 404 로 끊기면 안 된다 — 존재 여부만 본다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistListRow(
                       a.artistSeq, u.userSeq, u.nickname, u.profileImageKey,
                       g.groupSeq, g.groupName, c.companySeq, c.companyName,
                       a.bio, a.motifBgImageKey,
                       a.subscriberCount, a.deleted, a.regDttm)
            from Artist a
              join User u on u.userSeq = a.userSeq
              left join ArtistGroup g on g.groupSeq = a.groupSeq
              join Company c on c.companySeq = a.companySeq
            where a.artistSeq = :artistSeq
            """)
    Optional<ArtistListRow> findRow(@Param("artistSeq") Integer artistSeq);

    /**
     * 아티스트 목록·검색 (§3.5.1) — 이름·그룹·소속사 필터 + offset 페이지.
     *
     * <p>{@code users} 는 {@code uq_artists_user} 로 1:1 이라 inner join, {@code group_seq}
     * 는 솔로면 {@code null} 이라 left join 이다. 연관을 매핑하는 대신 ON 조인을 쓴다
     * ({@link Artist} javadoc — 목록 아닌 조회까지 조인이 따라붙는 것을 피한다).
     *
     * <p>{@code q} 는 {@code users.nickname} 부분 일치다. 0012 의 GIN trigram 인덱스가
     * {@code ILIKE '%…%'} 를 받쳐 준다 — 앞뒤 와일드카드라 B-tree 로는 못 탄다.
     *
     * <p>정렬을 {@code artistSeq DESC} 로 고정한다. {@link Pageable} 의 sort 를 그대로
     * 흘리면 클라이언트가 임의 칼럼으로 정렬해 인덱스 없는 풀스캔을 유발할 수 있고,
     * 화면(§1.7)이 요구하는 것은 최신 등록순 하나다.
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.artist.ArtistListRow(
                       a.artistSeq, u.userSeq, u.nickname, u.profileImageKey,
                       g.groupSeq, g.groupName, c.companySeq, c.companyName,
                       a.bio, a.motifBgImageKey,
                       a.subscriberCount, a.deleted, a.regDttm)
            from Artist a
              join User u on u.userSeq = a.userSeq
              left join ArtistGroup g on g.groupSeq = a.groupSeq
              join Company c on c.companySeq = a.companySeq
            where (:includeDeleted = true or a.deleted = false)
              and (:groupSeq is null or a.groupSeq = :groupSeq)
              and (:companySeq is null or a.companySeq = :companySeq)
              and (:q is null or lower(u.nickname) like lower(concat('%', :q, '%')))
            order by a.artistSeq desc
            """,
            countQuery = """
            select count(a)
            from Artist a
              join User u on u.userSeq = a.userSeq
            where (:includeDeleted = true or a.deleted = false)
              and (:groupSeq is null or a.groupSeq = :groupSeq)
              and (:companySeq is null or a.companySeq = :companySeq)
              and (:q is null or lower(u.nickname) like lower(concat('%', :q, '%')))
            """)
    Page<ArtistListRow> search(@Param("q") String q,
                               @Param("groupSeq") Integer groupSeq,
                               @Param("companySeq") Integer companySeq,
                               @Param("includeDeleted") boolean includeDeleted,
                               Pageable pageable);

    /**
     * 아티스트 표시 이름 — {@code users.nickname} 이다.
     *
     * <p>{@code artists} 에 이름 칸이 없다({@link Artist} javadoc). 연관을 매핑하는 대신
     * 두 엔티티를 조인해 필요한 두 값만 꺼낸다 — 어드민 챌린지 상세(§3.7.3)는 메인
     * 아티스트 1명 + 슬롯 최대 4명의 이름만 있으면 되고, 그 때문에 {@code Artist} 에
     * {@code @ManyToOne} 을 달면 목록 조회마다 조인이 따라붙는다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistName(a.artistSeq, u.nickname)
            from Artist a, User u
            where a.userSeq = u.userSeq and a.artistSeq in :artistSeqs
            """)
    List<ArtistName> findNames(@Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * 요청된 아티스트가 <b>전부</b> 존재하고 비삭제인지 확인하기 위한 개수 조회.
     *
     * <p>개수만 세는 이유: 어느 것이 없는지 알아도 응답에 담지 않는다(명세 §3.3 은
     * 코드만 반환). 행을 끌어오지 않아 존재 검증으로 충분하다. 호출자는 요청한
     * <b>중복 제거된</b> 개수와 비교한다.
     */
    long countByArtistSeqInAndDeletedFalse(Collection<Integer> artistSeqs);

    /**
     * 구독자 수 증가 — <b>원자적 UPDATE</b> (명세 §3.3, §5.6).
     *
     * <p>엔티티를 읽어 {@code count + 1} 을 써넣으면 동시 구독에서 증가분이 유실된다
     * (lost update). DB 가 현재 값을 기준으로 더하게 맡긴다.
     *
     * <p><b>두 플래그가 같이 있어야 한다.</b>
     * <ul>
     *   <li>{@code clearAutomatically} — 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로,
     *       같은 트랜잭션에서 이미 로드된 {@code Artist} 는 옛 카운터를 들고 있게 된다.
     *       실행 후 컨텍스트를 비워 다음 조회가 DB 값을 읽게 한다.</li>
     *   <li>{@code flushAutomatically} — <b>기본값이 false 다.</b> 이게 없으면 같은
     *       트랜잭션에서 아직 flush 되지 않은 INSERT/UPDATE 가 위 clear 로 <b>통째로
     *       버려진다.</b> §3.3 온보딩이 정확히 이 경우다 — {@code subscriptions} 를
     *       저장한 직후 이 메서드를 부르므로, flush 를 먼저 하지 않으면 구독이
     *       DB 에 남지 않는다(통합 테스트로 확인된 실제 버그).</li>
     * </ul>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Artist a set a.subscriberCount = a.subscriberCount + 1 where a.artistSeq in :artistSeqs")
    int increaseSubscriberCount(@Param("artistSeqs") Collection<Integer> artistSeqs);
}

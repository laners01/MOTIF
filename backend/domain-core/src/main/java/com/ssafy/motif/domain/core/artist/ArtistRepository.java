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
     * <p><b>{@code q} 에 {@code null} 을 넘기지 않는다.</b> 검색어가 없으면 호출부가 빈
     * 문자열로 정규화해 넘기고, {@code like '%%'} 가 전체 조회가 된다. {@code :q is null}
     * 분기를 두면 타입 없는 {@code null} 을 Postgres 가 {@code bytea} 로 추론해
     * {@code function lower(bytea) does not exist} 로 죽는다 — 2026-09-13 운영에서 실제로
     * 500 이 났다(같은 함정을 {@code SongRepository.search} 도 밟았다).
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
              and lower(u.nickname) like lower(concat('%', :q, '%'))
            order by a.artistSeq desc
            """,
            countQuery = """
            select count(a)
            from Artist a
              join User u on u.userSeq = a.userSeq
            where (:includeDeleted = true or a.deleted = false)
              and (:groupSeq is null or a.groupSeq = :groupSeq)
              and (:companySeq is null or a.companySeq = :companySeq)
              and lower(u.nickname) like lower(concat('%', :q, '%'))
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

    /** 소속사 삭제 차단 (§3.3.4 {@code COMPANY_IN_USE}) — 비삭제 아티스트가 남아 있으면 막는다. */
    long countByCompanySeqAndDeletedFalse(Integer companySeq);

    /** 그룹 삭제 차단 (§3.4.4 {@code GROUP_IN_USE}) — 비삭제 멤버가 남아 있으면 막는다. */
    long countByGroupSeqAndDeletedFalse(Integer groupSeq);

    /**
     * 그룹 소속사 이동에 멤버를 따라 옮긴다 (§3.4.3, DB §8).
     *
     * <p>"그룹 소속 아티스트의 소속사 = 그룹 소속사" 불변식 때문에 그룹만 옮기면 멤버가
     * 어긋난다. 그래서 {@link ArtistGroup#changeCompany} 와 <b>같은 트랜잭션</b>에서 부른다.
     * 멤버 수만큼 UPDATE 를 날리는 대신 한 방 벌크다 — 그룹 하나에 멤버가 수십이어도 1회다.
     *
     * <p><b>두 플래그가 같이 있어야 한다</b>({@link #increaseSubscriberCount} 와 같은 이유).
     * {@code flushAutomatically} 가 없으면 바로 앞에서 바꾼 {@code ArtistGroup.companySeq}
     * 가 아직 flush 되지 않은 채 {@code clearAutomatically} 로 <b>버려진다</b> — 멤버만
     * 옮겨지고 그룹은 옛 소속사에 남는, 정확히 막으려던 어긋남이 된다.
     *
     * @return 옮겨진 멤버 수 — 응답 {@code memberCount} 검증용이 아니라 감사 로그용이다
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Artist a set a.companySeq = :companySeq
            where a.groupSeq = :groupSeq and a.deleted = false
            """)
    int moveMembersCompany(@Param("groupSeq") Integer groupSeq,
                           @Param("companySeq") Integer companySeq);

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

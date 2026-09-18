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
            select new com.ssafy.motif.domain.core.artist.ArtistName(a.artistSeq, u.nickname, u.profileImageKey)
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
     * 랭킹 카드 한 페이지 분 (명세 §5.1) — 구독 목록(§4.6)과 같은 투영이다
     * ({@link ArtistCardRow}).
     *
     * <p><b>순서를 보장하지 않는다.</b> 랭킹 순서는 Redis 스냅샷이 정하고 여기는 메타만
     * 채운다 — 호출부가 {@code artistSeq} 로 묶어 자기 순서에 끼운다. {@code in} 절에
     * {@code order by} 를 붙여 봐야 스냅샷 순서와는 무관하다.
     *
     * <p>삭제 아티스트를 거르지 않는다 — 스냅샷에 비삭제만 들어가므로 이미 걸러져 있고,
     * 여기서 또 걸면 "스냅샷에는 있는데 메타가 없는" 항목이 조용히 사라진다. 그런 일이
     * 생긴다면 스냅샷이 낡은 것이고, 그건 호출부가 다룬다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCardRow(
                       a.artistSeq, u.nickname, u.profileImageKey, g.groupName)
            from Artist a
              join User u on u.userSeq = a.userSeq
              left join ArtistGroup g on g.groupSeq = a.groupSeq
            where a.artistSeq in :artistSeqs
            """)
    List<ArtistCardRow> findCards(@Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * 전체 아티스트 목록 (명세 §5.10) — <b>구독자 수 내림차순 전량</b>, 비삭제만.
     *
     * <p>투영은 {@link #findCards} 와 같은 {@link ArtistCardRow} 다 — 필요한 칸이 정확히
     * 같아서 새 record 를 만들지 않았다. 대신 <b>{@code subscriberCount} 를 칸으로 더하지
     * 않는다</b>: §5.10 응답에 그 값이 없고, 정렬은 DB 가 한다. 한 칸을 더하면 이 투영을
     * 쓰는 §5.1·§4.6 이 쓰지도 않을 값을 함께 끌어온다.
     *
     * <p><b>동점 tie-break 가 쿼리 안에 있어야 한다.</b> {@code subscriber_count} 만으로
     * 정렬하면 구독자 0인 신인이 여러 명일 때 Postgres 가 주는 순서가 요청마다 달라져
     * 같은 화면이 새로고침마다 뒤바뀐다. §5.1 스냅샷의 동점 규칙과 같은
     * {@code artistSeq} ASC 를 쓴다.
     *
     * <p>{@link #search}(어드민 §3.5.1)와 나누어 둔 것은 정렬·필터·봉투가 전부 다르기
     * 때문이다 — 저쪽은 {@code artistSeq DESC} offset 페이지에 삭제분 포함 옵션이 있다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCardRow(
                       a.artistSeq, u.nickname, u.profileImageKey, g.groupName)
            from Artist a
              join User u on u.userSeq = a.userSeq
              left join ArtistGroup g on g.groupSeq = a.groupSeq
            where a.deleted = false
            order by a.subscriberCount desc, a.artistSeq asc
            """)
    List<ArtistCardRow> findCardsBySubscriberCountDesc();

    /**
     * 랭킹 스냅샷에 올릴 아티스트 전량 (명세 §5.1) — 비삭제만.
     *
     * <p><b>챌린지가 0개여도 포함한다.</b> 챌린지 쪽에서 집계해 얻은 목록만 쓰면 아직
     * 챌린지가 없는 아티스트가 스냅샷에서 빠지는데, 그러면 온보딩 인기 목록(§3.1)과
     * {@code UNSUBSCRIBED} 필터에서 신인이 통째로 사라진다. 점수 0 으로 넣는다.
     */
    @Query("select a.artistSeq from Artist a where a.deleted = false")
    List<Integer> findRankableSeqs();

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

    /**
     * 구독자 수 감소 — {@link #increaseSubscriberCount} 의 대칭 (명세 §5.7).
     *
     * <p>두 플래그가 필요한 이유는 증가 쪽과 같다. 특히 {@code clearAutomatically} 가
     * 없으면 <b>응답의 {@code subscriberCount} 가 감소 전 값으로 나간다</b> — 벌크
     * UPDATE 는 영속성 컨텍스트를 우회하므로, 같은 트랜잭션에서 이미 로드된
     * {@code Artist} 가 옛 카운터를 들고 있는 채로 조회에 쓰인다.
     *
     * <p><b>{@code count > 0} 조건을 달지 않는다.</b> 방어처럼 보이지만 오히려 위험하다 —
     * 카운터가 0 인데 구독 행이 남아 있는 어긋난 상태에서 UPDATE 가 조용히 0행이 되고,
     * 그러면 거울 카운터가 틀어졌다는 사실이 드러나지 않는다.
     * {@code ck_artists_subscriber_count CHECK (subscriber_count >= 0)} 이 터지게 두는 편이
     * 낫다. 애초에 호출부가 <b>실제로 지운 경우에만</b> 부르므로
     * ({@link SubscriptionRepository#deleteIfPresent} 의 반환값) 정상 경로에서는 음수가 되지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Artist a set a.subscriberCount = a.subscriberCount - 1 where a.artistSeq = :artistSeq")
    int decreaseSubscriberCount(@Param("artistSeq") Integer artistSeq);
}

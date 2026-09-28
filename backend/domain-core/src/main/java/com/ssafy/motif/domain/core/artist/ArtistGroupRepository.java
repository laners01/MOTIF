package com.ssafy.motif.domain.core.artist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArtistGroupRepository extends JpaRepository<ArtistGroup, Integer> {

    /** 삭제된 그룹에는 아티스트를 붙이지 않는다 (§3.5.2 검증) — 수정·삭제 대상 조회도 같다. */
    Optional<ArtistGroup> findByGroupSeqAndDeletedFalse(Integer groupSeq);

    /** 소속사 삭제 차단 (§3.3.4 {@code COMPANY_IN_USE}) — 비삭제 그룹이 남아 있으면 막는다. */
    long countByCompanySeqAndDeletedFalse(Integer companySeq);

    /**
     * 그룹 목록·검색 (§3.4.1) — 소속사 필터 + 그룹명 부분 일치 + offset 페이지.
     *
     * <p>멤버 수는 상관 서브쿼리로 같은 질의에서 센다({@link CompanyRepository#search} 와
     * 같은 이유). 소속사는 FK NOT NULL 이라 inner join 이다.
     *
     * <p>{@code group_name} 은 0012 의 GIN trigram 인덱스({@code gin_groups_name})가
     * {@code ILIKE '%…%'} 를 받쳐 준다 — 앞뒤 와일드카드라 B-tree 로는 못 탄다.
     *
     * <p><b>{@code q} 에 {@code null} 을 넘기지 않는다</b> — 빈 문자열로 정규화해 넘긴다.
     * {@code :q is null} 분기는 {@code lower(bytea)} 500 을 부른다
     * ({@link ArtistRepository#search} javadoc — 385·389 가 같은 함정을 밟았다).
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.artist.GroupListRow(
                       g.groupSeq, g.groupName, c.companySeq, c.companyName,
                       (select count(a) from Artist a
                         where a.groupSeq = g.groupSeq and a.deleted = false),
                       g.deleted, g.regDttm)
            from ArtistGroup g
              join Company c on c.companySeq = g.companySeq
            where (:includeDeleted = true or g.deleted = false)
              and (:companySeq is null or g.companySeq = :companySeq)
              and lower(g.groupName) like lower(concat('%', :q, '%'))
            order by g.groupSeq desc
            """,
            countQuery = """
            select count(g)
            from ArtistGroup g
            where (:includeDeleted = true or g.deleted = false)
              and (:companySeq is null or g.companySeq = :companySeq)
              and lower(g.groupName) like lower(concat('%', :q, '%'))
            """)
    Page<GroupListRow> search(@Param("q") String q,
                              @Param("companySeq") Integer companySeq,
                              @Param("includeDeleted") boolean includeDeleted,
                              Pageable pageable);

    /** 수정 응답으로 돌려줄 한 줄 (§3.4.3 — {@code data} 가 3.4.1 항목이다). 삭제분도 준다. */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.GroupListRow(
                       g.groupSeq, g.groupName, c.companySeq, c.companyName,
                       (select count(a) from Artist a
                         where a.groupSeq = g.groupSeq and a.deleted = false),
                       g.deleted, g.regDttm)
            from ArtistGroup g
              join Company c on c.companySeq = g.companySeq
            where g.groupSeq = :groupSeq
            """)
    Optional<GroupListRow> findRow(@Param("groupSeq") Integer groupSeq);
}

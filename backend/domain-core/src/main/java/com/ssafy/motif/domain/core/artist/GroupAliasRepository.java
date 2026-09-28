package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 그룹 별칭 리포지토리 ({@link GroupAlias}).
 *
 * <p>구성은 {@link ArtistAliasRepository} 와 같다 — 검색 쿼리는 §5.2 티켓이 갖고, 여기는
 * 어드민 CRUD(어드민 §3.4.5~8)가 쓰는 만큼만 둔다.
 */
public interface GroupAliasRepository extends JpaRepository<GroupAlias, Integer> {

    /** 어드민 별칭 목록 (§3.4.5) — 비페이지 전량. */
    List<GroupAlias> findByGroupSeqOrderByAliasSeq(Integer groupSeq);

    /** 수정·삭제 대상 조회 (§3.4.7·§3.4.8). 소유 확인 근거는 {@link ArtistAliasRepository}. */
    Optional<GroupAlias> findByAliasSeqAndGroupSeq(Integer aliasSeq, Integer groupSeq);
}

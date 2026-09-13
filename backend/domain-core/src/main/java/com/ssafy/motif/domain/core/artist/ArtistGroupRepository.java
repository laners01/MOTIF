package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistGroupRepository extends JpaRepository<ArtistGroup, Integer> {

    /** 삭제된 그룹에는 아티스트를 붙이지 않는다 (§3.5.2 검증). */
    Optional<ArtistGroup> findByGroupSeqAndDeletedFalse(Integer groupSeq);

    /** 소속사 삭제 차단 (§3.3.4 {@code COMPANY_IN_USE}) — 비삭제 그룹이 남아 있으면 막는다. */
    long countByCompanySeqAndDeletedFalse(Integer companySeq);
}

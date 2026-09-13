package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Integer> {

    /** 삭제된 소속사에는 아티스트를 붙이지 않는다 (§3.5.2 검증). */
    Optional<Company> findByCompanySeqAndDeletedFalse(Integer companySeq);
}

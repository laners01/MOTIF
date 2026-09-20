package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 아티스트 별칭 리포지토리 ({@link ArtistAlias}).
 *
 * <p><b>검색 쿼리를 여기 두지 않는다.</b> API §5.2 는 두 별칭 테이블을 훑어 그룹 멤버를
 * 펼치고 5단 정렬을 거는 네이티브 쿼리라, 그 티켓이 자기 것으로 가져간다. 여기는 어드민
 * CRUD(어드민 §3.5.6~9)가 쓰는 만큼만 둔다.
 */
public interface ArtistAliasRepository extends JpaRepository<ArtistAlias, Integer> {

    /** 어드민 별칭 목록 (§3.5.6) — 비페이지 전량. 한 아티스트당 몇 개 수준이다. */
    List<ArtistAlias> findByArtistSeqOrderByAliasSeq(Integer artistSeq);

    /**
     * 수정·삭제 대상 조회 (§3.5.8·§3.5.9).
     *
     * <p><b>{@code aliasSeq} 만으로 찾지 않는다.</b> 경로의 아티스트 소유인지 함께 보지
     * 않으면 남의 별칭을 {@code aliasSeq} 하나로 고치는 경로가 열린다 — 없으면 호출부가
     * {@code ALIAS_NOT_FOUND} 를 던진다.
     */
    Optional<ArtistAlias> findByAliasSeqAndArtistSeq(Integer aliasSeq, Integer artistSeq);
}

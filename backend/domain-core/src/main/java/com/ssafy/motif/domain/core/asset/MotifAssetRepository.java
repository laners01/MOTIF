package com.ssafy.motif.domain.core.asset;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Collection;

public interface MotifAssetRepository extends JpaRepository<MotifAsset, Integer> {

    /**
     * 아티스트 목록·상세의 {@code assetCount} (어드민 §3.5.1) — 페이지 전체를 한 번에.
     *
     * <p>비활성 에셋도 센다. {@code motif_assets} 에는 삭제 플래그가 없고(DB §30)
     * 운영 지표로 보고 싶은 것은 "이 아티스트가 가진 에셋 수" 이기 때문이다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(a.artistSeq, count(a))
            from MotifAsset a
            where a.artistSeq in :artistSeqs
            group by a.artistSeq
            """)
    List<ArtistCount> countByArtists(@Param("artistSeqs") Collection<Integer> artistSeqs);
}

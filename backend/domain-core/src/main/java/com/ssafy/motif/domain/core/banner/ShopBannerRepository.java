package com.ssafy.motif.domain.core.banner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopBannerRepository extends JpaRepository<ShopBanner, Integer> {

    /** §3.11.1 — 전량(비활성 포함), {@code display_order ASC}. */
    List<ShopBanner> findAllByOrderByDisplayOrderAsc();

    boolean existsByDisplayOrder(short displayOrder);

    /** 등록 시 {@code displayOrder} 생략분에 줄 {@code MAX+1} 의 재료. 비어 있으면 empty. */
    Optional<ShopBanner> findFirstByOrderByDisplayOrderDesc();

    /**
     * 재정렬 1단계 — 전 행의 순서를 한꺼번에 밀어 올린다 (§3.11.4).
     *
     * <p><b>UNIQUE 충돌을 피하려고 두 단계로 나눈다.</b> 새 순서를 곧바로 쓰면 중간 상태에서
     * 기존 행과 부딪힌다(1↔2 를 맞바꾸는 것만으로 걸린다). {@code offset} 이 현재 최대값보다
     * 크므로 이 한 문장 안에서는 어떤 행도 서로 부딪히지 않는다.
     *
     * <p><b>{@code clearAutomatically} 가 필수다.</b> 벌크 UPDATE 는 영속성 컨텍스트를
     * 지나치므로, 비우지 않으면 메모리의 엔티티가 옛 순서를 그대로 들고 있다. 그 상태에서
     * 2단계가 "원래 값과 같은" 순서를 넣으면 더티 체킹이 변경으로 보지 않아 <b>그 행만
     * 밀어 올려진 채 남는다</b> — 예외도 로그도 없이 순서가 깨진다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShopBanner b set b.displayOrder = b.displayOrder + :offset")
    void shiftAllBy(@Param("offset") short offset);
}

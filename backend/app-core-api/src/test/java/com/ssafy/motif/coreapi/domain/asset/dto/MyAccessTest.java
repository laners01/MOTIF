package com.ssafy.motif.coreapi.domain.asset.dto;

import com.ssafy.motif.domain.core.asset.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §11.1·§11.2 {@code myAccess} 우선순위 (S15P21D104-224).
 *
 * <p>DB 가 필요 없는 규칙이라 여기서 고정한다 — 쿼리는 "구매가 있는가"·"유효 패스가
 * 있는가"까지만 답하고, 둘을 한 값으로 접는 판단은 전부 이 클래스에 있다.
 */
class MyAccessTest {

    @Test
    @DisplayName("구매와 패스가 동시에 유효하면 OWNED 다 — 패스가 끝나도 남는 권한을 답한다")
    void purchaseWinsOverPass() {
        assertThat(MyAccess.of(true, true, AssetType.NORMAL)).isEqualTo(MyAccess.OWNED);
    }

    @Test
    @DisplayName("패스만 있으면 PASS")
    void passOnly() {
        assertThat(MyAccess.of(false, true, AssetType.NORMAL)).isEqualTo(MyAccess.PASS);
    }

    @Test
    @DisplayName("PREMIUM 은 유효 패스가 있어도 NONE 이다 — 개별 구매 전용(R5)")
    void premiumIsNotCoveredByPass() {
        assertThat(MyAccess.of(false, true, AssetType.PREMIUM)).isEqualTo(MyAccess.NONE);
    }

    @Test
    @DisplayName("PREMIUM 도 사 두었으면 OWNED 다 — 커버 규칙은 패스에만 걸린다")
    void premiumCanStillBeOwned() {
        assertThat(MyAccess.of(true, true, AssetType.PREMIUM)).isEqualTo(MyAccess.OWNED);
    }

    @Test
    @DisplayName("SECRET 은 NORMAL 과 같다 — 패스가 커버한다")
    void secretBehavesLikeNormal() {
        assertThat(MyAccess.of(false, true, AssetType.SECRET)).isEqualTo(MyAccess.PASS);
    }

    @Test
    @DisplayName("둘 다 없으면 NONE")
    void noneWithoutAnything() {
        assertThat(MyAccess.of(false, false, AssetType.NORMAL)).isEqualTo(MyAccess.NONE);
    }
}

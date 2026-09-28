package com.ssafy.motif.coreapi.admin.actas;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 대행 컨텍스트 해석 (어드민 명세 §3.12) 를 실제 DB에 대고 검증한다 (S15P21D104-407).
 *
 * <p>확인하는 것은 둘 — ① 액터가 {@code artistSeq} 가 아니라 {@code artists.user_seq}
 * 인가 ② 삭제된 아티스트가 404 인가. ①이 깨지면 대행이 <b>남의 계정</b>으로 댓글을
 * 다는데, 두 시퀀스가 작은 수에서 겹쳐 보여 로컬에서는 우연히 통과한다 —
 * 그래서 두 값이 서로 다른 픽스처로 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ActAsArtistResolverLocalDbTest {

    @Autowired
    private ActAsArtistResolver actAsArtistResolver;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("액터는 artistSeq 가 아니라 artists.user_seq 다")
    void resolvesToArtistUserSeq() {
        Artist artist = newArtist();

        Integer actor = actAsArtistResolver.actorUserSeq(artist.artistSeq());

        assertThat(actor).isEqualTo(artist.userSeq());
        assertThat(actor).isNotEqualTo(artist.artistSeq());
    }

    @Test
    @DisplayName("삭제된 아티스트도 없는 아티스트도 404 ARTIST_NOT_FOUND")
    void deletedOrUnknownIsNotFound() {
        Artist artist = newArtist();
        softDelete(artist.artistSeq());

        for (Integer artistSeq : new Integer[]{artist.artistSeq(), 999_999}) {
            assertThatThrownBy(() -> actAsArtistResolver.actorUserSeq(artistSeq))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
        }
    }

    // --- 픽스처 -------------------------------------------------------------

    private record Artist(Integer artistSeq, Integer userSeq) {
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인 (569 테스트와 같은 방식). */
    private Artist newArtist() {
        User artistUser = userRepository.saveAndFlush(User.signUp());
        artistUser.applyInitialNickname();
        ReflectionTestUtils.setField(artistUser, "role", UserRole.ARTIST);
        userRepository.flush();

        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", companyName)
                .setParameter("regUsrSeq", artistUser.getUserSeq())
                .executeUpdate();
        Integer companySeq = ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", companyName)
                .getSingleResult()).intValue();

        entityManager.createNativeQuery("""
                        insert into motif_core.artists (user_seq, company_seq, reg_usr_seq)
                        values (:userSeq, :companySeq, :regUsrSeq)
                        """)
                .setParameter("userSeq", artistUser.getUserSeq())
                .setParameter("companySeq", companySeq)
                .setParameter("regUsrSeq", artistUser.getUserSeq())
                .executeUpdate();

        Integer artistSeq = ((Number) entityManager.createNativeQuery(
                        "select artist_seq from motif_core.artists where user_seq = :userSeq")
                .setParameter("userSeq", artistUser.getUserSeq())
                .getSingleResult()).intValue();
        return new Artist(artistSeq, artistUser.getUserSeq());
    }

    private void softDelete(Integer artistSeq) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();
    }
}

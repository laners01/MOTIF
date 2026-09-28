package com.ssafy.motif.coreapi.domain.artist;

import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistAlias;
import com.ssafy.motif.domain.core.artist.ArtistAliasRepository;
import com.ssafy.motif.domain.core.artist.ArtistGroup;
import com.ssafy.motif.domain.core.artist.ArtistGroupRepository;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.Company;
import com.ssafy.motif.domain.core.artist.CompanyRepository;
import com.ssafy.motif.domain.core.artist.GroupAlias;
import com.ssafy.motif.domain.core.artist.GroupAliasRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 별칭 스키마·엔티티 검증 (DB SPEC §41·§42, changeSet 0018 — S15P21D104-357).
 *
 * <p>컨텍스트가 뜨는 것 자체가 {@code ddl-auto: validate} 통과다 — 엔티티와 DDL 의 타입이
 * 어긋나면 여기까지 오지 못한다. 그래서 이 클래스는 <b>validate 가 못 잡는 것</b>만 본다:
 * {@code CHAR(2)} 가 공백으로 패딩돼 돌아오지 않는지, UNIQUE 가 <b>없는지</b>, 소유 확인
 * 조회가 남의 별칭을 거르는지, 그리고 백필 SQL 이 삭제분을 빼는지.
 *
 * <p><b>공유 로컬 DB 다.</b> 전체 건수에 기대지 않는다 — 내가 만든 행만 골라 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ArtistAliasSchemaLocalDbTest {

    @Autowired
    private ArtistAliasRepository artistAliasRepository;
    @Autowired
    private GroupAliasRepository groupAliasRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private ArtistGroupRepository artistGroupRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer regUsrSeq;
    private Integer companySeq;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        regUsrSeq = user.getUserSeq();
        companySeq = companyRepository.saveAndFlush(
                Company.register(unique("c"), regUsrSeq)).getCompanySeq();
    }

    @Test
    @DisplayName("저장하고 읽으면 language_code 가 CHAR 패딩 없이 그대로다")
    void roundTrip() {
        Integer artistSeq = newArtist(null);

        Integer aliasSeq = artistAliasRepository.save(
                ArtistAlias.register(artistSeq, "en", "Karina", regUsrSeq)).getAliasSeq();
        flushAndClear();

        assertThat(artistAliasRepository.findById(aliasSeq).orElseThrow())
                .satisfies(alias -> {
                    // CHAR(2) 는 짧으면 공백으로 채워 돌아온다 — 'en' 이 'en' 이어야 한다
                    assertThat(alias.getLanguageCode()).isEqualTo("en");
                    assertThat(alias.getAlias()).isEqualTo("Karina");
                    assertThat(alias.getArtistSeq()).isEqualTo(artistSeq);
                    assertThat(alias.getRegUsrSeq()).isEqualTo(regUsrSeq);
                    assertThat(alias.getRegDttm()).isNotNull();
                    assertThat(alias.getModDttm()).isNull();   // 수정된 적 없음
                });
    }

    @Test
    @DisplayName("수정하면 두 칸이 함께 치환되고 mod_dttm 이 찍힌다")
    void change() {
        Integer artistSeq = newArtist(null);
        ArtistAlias alias = artistAliasRepository.saveAndFlush(
                ArtistAlias.register(artistSeq, "en", "Karina", regUsrSeq));

        alias.change("ja", "カリナ");
        flushAndClear();

        assertThat(artistAliasRepository.findById(alias.getAliasSeq()).orElseThrow())
                .satisfies(found -> {
                    assertThat(found.getLanguageCode()).isEqualTo("ja");
                    assertThat(found.getAlias()).isEqualTo("カリナ");
                    assertThat(found.getModDttm()).isNotNull();
                });
    }

    /**
     * UNIQUE 를 두지 않기로 한 결정(ADR-0014 D3)의 회귀 방어.
     *
     * <p>제약을 나중에 "정리" 차원에서 붙이면 어드민 등록이 409 로 막히는데, 한 언어에
     * 표기 변형이 여럿인 것이 정상이라(aespa·æspa) 그게 곧 기능 손실이다.
     */
    @Test
    @DisplayName("같은 아티스트·같은 언어에 별칭이 여러 개 — 완전히 같은 값도 막지 않는다")
    void duplicatesAreAllowed() {
        Integer artistSeq = newArtist(null);

        artistAliasRepository.save(ArtistAlias.register(artistSeq, "en", "aespa", regUsrSeq));
        artistAliasRepository.save(ArtistAlias.register(artistSeq, "en", "æspa", regUsrSeq));
        artistAliasRepository.save(ArtistAlias.register(artistSeq, "en", "aespa", regUsrSeq));
        flushAndClear();

        assertThat(artistAliasRepository.findByArtistSeqOrderByAliasSeq(artistSeq))
                .extracting(ArtistAlias::getAlias)
                .containsExactly("aespa", "æspa", "aespa");
    }

    @Test
    @DisplayName("소유 확인 조회는 남의 별칭을 주지 않는다")
    void ownershipIsChecked() {
        Integer mine = newArtist(null);
        Integer other = newArtist(null);
        ArtistAlias alias = artistAliasRepository.saveAndFlush(
                ArtistAlias.register(mine, "ko", "카리나", regUsrSeq));

        assertThat(artistAliasRepository.findByAliasSeqAndArtistSeq(alias.getAliasSeq(), mine))
                .isPresent();
        assertThat(artistAliasRepository.findByAliasSeqAndArtistSeq(alias.getAliasSeq(), other))
                .isEmpty();
    }

    @Test
    @DisplayName("별칭 하드 삭제는 아티스트를 건드리지 않는다")
    void hardDeleteLeavesArtist() {
        Integer artistSeq = newArtist(null);
        ArtistAlias alias = artistAliasRepository.saveAndFlush(
                ArtistAlias.register(artistSeq, "ko", "카리나", regUsrSeq));

        artistAliasRepository.delete(alias);
        flushAndClear();

        assertThat(artistAliasRepository.findById(alias.getAliasSeq())).isEmpty();
        assertThat(artistRepository.findByArtistSeqAndDeletedFalse(artistSeq)).isPresent();
    }

    @Test
    @DisplayName("그룹 별칭도 같은 계약이다 — 저장·소유 확인·중복 허용")
    void groupAlias() {
        Integer groupSeq = newGroup();
        Integer otherSeq = newGroup();

        GroupAlias alias = groupAliasRepository.saveAndFlush(
                GroupAlias.register(groupSeq, "en", "aespa", regUsrSeq));
        groupAliasRepository.saveAndFlush(GroupAlias.register(groupSeq, "en", "æspa", regUsrSeq));
        flushAndClear();

        assertThat(groupAliasRepository.findByGroupSeqOrderByAliasSeq(groupSeq))
                .extracting(GroupAlias::getAlias)
                .containsExactly("aespa", "æspa");
        assertThat(groupAliasRepository.findByAliasSeqAndGroupSeq(alias.getAliasSeq(), groupSeq))
                .isPresent();
        assertThat(groupAliasRepository.findByAliasSeqAndGroupSeq(alias.getAliasSeq(), otherSeq))
                .isEmpty();
    }

    /**
     * 0018-04 백필 SQL 의 회귀 방어 — <b>비삭제만</b> 채운다(2026-09-20 확정).
     *
     * <p>changeSet 은 이미 실행돼 있어 다시 돌릴 수 없으므로 같은 SQL 을 여기서 실행한다.
     * 공유 DB 를 건드리지 않도록 {@code IN} 절로 대상만 좁혔고, 검증 대상인 <b>조인과
     * {@code is_deleted} 필터는 그대로</b>다.
     */
    @Test
    @DisplayName("백필은 비삭제 아티스트·그룹만 현재 이름으로 채운다")
    void backfillSkipsDeleted() {
        Integer live = newArtist(null);
        Integer deleted = newArtist(null);
        softDelete("artists", "artist_seq", deleted);

        Integer liveGroup = newGroup();
        Integer deletedGroup = newGroup();
        softDelete("artist_groups", "group_seq", deletedGroup);
        flushAndClear();

        entityManager.createNativeQuery("""
                        insert into motif_core.artist_aliases
                               (artist_seq, language_code, alias, reg_usr_seq)
                        select a.artist_seq, 'ko', u.nickname, a.reg_usr_seq
                          from motif_core.artists a
                          join motif_core.users u on u.user_seq = a.user_seq
                         where a.is_deleted = false
                           and a.artist_seq in (:live, :deleted)
                        """)
                .setParameter("live", live)
                .setParameter("deleted", deleted)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        insert into motif_core.group_aliases
                               (group_seq, language_code, alias, reg_usr_seq)
                        select g.group_seq, 'ko', g.group_name, g.reg_usr_seq
                          from motif_core.artist_groups g
                         where g.is_deleted = false
                           and g.group_seq in (:live, :deleted)
                        """)
                .setParameter("live", liveGroup)
                .setParameter("deleted", deletedGroup)
                .executeUpdate();
        flushAndClear();

        List<ArtistAlias> filled = artistAliasRepository.findByArtistSeqOrderByAliasSeq(live);
        assertThat(filled).singleElement().satisfies(alias -> {
            assertThat(alias.getLanguageCode()).isEqualTo("ko");
            assertThat(alias.getAlias()).isEqualTo(nicknameOf(live));
            assertThat(alias.getRegUsrSeq()).isEqualTo(regUsrSeq);
        });
        assertThat(artistAliasRepository.findByArtistSeqOrderByAliasSeq(deleted)).isEmpty();

        assertThat(groupAliasRepository.findByGroupSeqOrderByAliasSeq(liveGroup))
                .singleElement()
                .satisfies(alias -> assertThat(alias.getLanguageCode()).isEqualTo("ko"));
        assertThat(groupAliasRepository.findByGroupSeqOrderByAliasSeq(deletedGroup)).isEmpty();
    }

    // --- 픽스처 -------------------------------------------------------------

    /** 공유 DB 라 이름이 겹치면 안 된다 — 회사명은 활성 부분 UNIQUE 다(칼럼 50자). */
    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private Integer newArtist(Integer groupSeq) {
        User user = userRepository.save(User.createArtist(unique("a")));
        return artistRepository.save(Artist.register(
                user.getUserSeq(), groupSeq, companySeq, null, null, regUsrSeq)).getArtistSeq();
    }

    private Integer newGroup() {
        return artistGroupRepository.save(
                ArtistGroup.register(companySeq, unique("g"), regUsrSeq)).getGroupSeq();
    }

    private String nicknameOf(Integer artistSeq) {
        Artist artist = artistRepository.findById(artistSeq).orElseThrow();
        return userRepository.findById(artist.getUserSeq()).orElseThrow().getNickname();
    }

    private void softDelete(String table, String idColumn, Integer seq) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update motif_core." + table + " set is_deleted = true"
                                + " where " + idColumn + " = :seq")
                .setParameter("seq", seq)
                .executeUpdate();
        entityManager.clear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}

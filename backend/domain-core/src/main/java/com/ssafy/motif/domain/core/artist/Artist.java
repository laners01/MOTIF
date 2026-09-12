package com.ssafy.motif.domain.core.artist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아티스트 (DB SPEC v0.15 §9, changeSet 0003-01).
 *
 * <p><b>표시 이름이 이 테이블에 없다.</b> {@code artists} 는 {@code users.role='ARTIST'}
 * 사용자의 도메인 확장이라({@code uq_artists_user}) 이름은 {@code users.nickname} 이다.
 * 이름·그룹·소속사가 필요한 §5(아티스트 조회·검색)에서는 {@code users} 조인이 전제된다.
 *
 * <p><b>부분 매핑이다.</b> §3.3 온보딩에 필요한 컬럼만 매핑했다 —
 * {@code user_seq}·{@code group_seq}·{@code company_seq}·{@code bio}·
 * {@code motif_bg_image_key} 등은 §5·§11 티켓에서 연관과 함께 추가한다.
 * 매핑하지 않은 컬럼은 {@code ddl-auto: validate} 대상이 아니라 기동에 영향이 없다.
 *
 * <p>{@code subscriberCount} 는 거울 카운터다. 구독 생성·해제와 <b>같은 트랜잭션</b>에서
 * 원자적 UPDATE 로 증감한다 — 읽고-더하고-쓰면 동시 구독에서 증가분이 유실된다
 * ({@link ArtistRepository#increaseSubscriberCount}).
 */
@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_seq")
    private Integer artistSeq;

    @Column(name = "subscriber_count", nullable = false)
    private int subscriberCount;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}

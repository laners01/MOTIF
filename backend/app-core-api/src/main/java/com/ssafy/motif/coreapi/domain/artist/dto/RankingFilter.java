package com.ssafy.motif.coreapi.domain.artist.dto;

/**
 * 랭킹 목록 필터 (API 명세 §5.1).
 *
 * <p><b>Redis 가 아니라 애플리케이션 단계에서 적용한다.</b> 구독 정보는 스냅샷에 없고,
 * 페이지를 자르기 <b>전에</b> 걸러야 페이지 크기가 일정해진다 — 자른 뒤에 거르면
 * {@code size=20} 요청에 3건이 담기는 일이 생긴다.
 */
public enum RankingFilter {

    ALL,

    /** 내가 구독 중인 아티스트만. */
    SUBSCRIBED,

    /** 내가 구독하지 않은 아티스트만 — 온보딩·발견 화면용. */
    UNSUBSCRIBED;

    /** 이 항목을 목록에 남길지 — 분기를 호출부에 흩지 않으려고 enum 이 갖는다. */
    public boolean matches(boolean subscribed) {
        return switch (this) {
            case ALL -> true;
            case SUBSCRIBED -> subscribed;
            case UNSUBSCRIBED -> !subscribed;
        };
    }
}

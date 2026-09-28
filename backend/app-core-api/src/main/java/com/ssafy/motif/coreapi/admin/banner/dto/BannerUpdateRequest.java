package com.ssafy.motif.coreapi.admin.banner.dto;

/**
 * §3.11.3 부분 수정 — 이미지 교체·활성 전환. 보낸 필드만 반영한다.
 *
 * <p>순서는 여기서 바꾸지 않는다. 한 건만 옮기면 UNIQUE 때문에 다른 행과 반드시
 * 부딪히므로 재정렬은 전량 교체(§3.11.4)로만 한다.
 */
public record BannerUpdateRequest(String imageKey, Boolean isActive) {

    public boolean isEmpty() {
        return imageKey == null && isActive == null;
    }
}

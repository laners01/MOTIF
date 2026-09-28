package com.ssafy.motif.coreapi.admin.notice.dto;

/** 공지 이미지 (어드민 명세 §3.10.3 {@code images[]}). */
public record NoticeImageResponse(String imageUrl, short displayOrder) {
}

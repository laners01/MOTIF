package com.ssafy.motif.coreapi.admin.notice.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 공지 수정 요청 (어드민 명세 §3.10.4) — 부분 수정.
 *
 * @param content   {@code null} 이면 본문 불변. 값이 오면 빈 문자열은 막는다
 *                  ({@code notices.content} 는 NOT NULL 이고 빈 공지는 의미가 없다)
 * @param imageKeys <b>{@code null} 이면 이미지 불변, 오면 전체 교체</b>다 — 기존 행을
 *                  하드 삭제하고 배열 순서대로 {@code display_order} 0..n−1 로 다시
 *                  만든다(DB §28). 빈 배열은 "전부 지움"이라는 뜻이라 유효한 입력이다
 */
public record NoticeUpdateRequest(
        @Size(max = 5000) String content,
        @Size(max = 10) List<@Size(max = 255) String> imageKeys
) {
}

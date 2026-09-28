package com.ssafy.motif.coreapi.domain.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * §7.6 편집본 영상 — 촬영 화면이 배경으로 재생한다.
 *
 * <p><b>HLS 가 아니라 통짜 MP4 다.</b> 편집본은 {@code job.media} 를 타지 않아 렌디션이
 * 없다 — 사다리를 만들 이유가 없었다. 15초 내외 한 편을 촬영 시작 전에 받아 두는 쓰임이라
 * 재생 중 화질 전환이 필요 없고, 같은 파일을 AI 워커도 합성 베이스로 받는다(그쪽은 MP4 만
 * 읽는다). ABR 이 필요한 것은 피드처럼 <b>연속 재생</b>되는 영상이다(ADR-0010 D2).
 *
 * <p>대신 <b>서명이 붙는다</b>(0020) — 키가 {@code private/edited/…} 라 CloudFront 캐시
 * 동작 {@code private/*} 이 뷰어 접근을 막고, {@code MediaUrlResolver} 가 URL 하나를
 * 서명해 내려준다. 단일 파일이라 재생목록처럼 조각마다 서명을 이어 붙일 필요가 없다.
 *
 * @param durationMs 편집본 길이. <b>MP4 라서 서버가 준다</b> — HLS 면 재생목록의
 *                   {@code #EXTINF} 합으로 플레이어가 알아내지만, MP4 는 {@code moov} 를
 *                   읽기 전까지 모른다. 촬영 타이머를 재생 전에 그리려면 필요하다
 * @param expiresIn  서명 만료까지 남은 초. 지나면 §7.6 을 다시 불러 새 URL 을 받는다
 */
@Schema(description = "편집본 영상 (통짜 MP4 · 서명 URL)")
public record EditedVideoResponse(

        Integer challengeParticipantSeq,

        Integer challengeSeq,

        @Schema(description = "이 슬롯에서 지워진 아티스트", example = "7")
        Integer artistSeq,

        @Schema(description = "편집본 영상 — 단일 MP4 의 서명된 CloudFront URL")
        String videoUrl,

        @Schema(description = "편집본 길이(ms)", example = "15020")
        Integer durationMs,

        @Schema(description = "서명 만료까지 남은 초", example = "600")
        long expiresIn
) {
}

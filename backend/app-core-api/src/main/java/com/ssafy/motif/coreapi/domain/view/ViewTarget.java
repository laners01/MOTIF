package com.ssafy.motif.coreapi.domain.view;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 조회수를 세는 대상 (ADR-0015 · ADR-0016).
 *
 * <p><b>왜 열거형 하나로 묶었는가.</b> 참여 영상(§8.5)과 챌린지(§7.4)는 계약도 집계
 * 흐름도 같다. 버퍼를 복제하면 {@code RENAMENX} rotate · 멱등 마커 · 재처리 인덱스가
 * <b>두 벌</b>이 되어 한쪽만 고쳐졌을 때 조용히 갈린다. 다른 것은 Redis 키 프리픽스와
 * DB 테이블뿐이라 그 둘만 여기 둔다.
 *
 * <p><b>SQL 식별자를 여기서 만든다.</b> 값이 열거형 상수라 외부 입력이 섞일 자리가
 * 없다 — {@link ViewCounterStore} 가 이 값으로 문장을 조립한다.
 *
 * @param key    Redis 키의 대상 구간. {@code view:{key}:pending} 처럼 쓰인다
 * @param table  {@code motif_core} 안의 테이블명
 * @param idColumn 그 테이블의 PK 칼럼
 */
@Getter
@RequiredArgsConstructor
public enum ViewTarget {

    /** 참여 영상 (API 명세 §8.5). */
    VIDEO("video", "participation_videos", "video_seq"),

    /** 원본 챌린지 (API 명세 §7.4). */
    CHALLENGE("challenge", "challenges", "challenge_seq");

    private final String key;
    private final String table;
    private final String idColumn;
}

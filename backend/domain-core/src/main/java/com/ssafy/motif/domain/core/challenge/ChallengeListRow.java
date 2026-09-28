package com.ssafy.motif.domain.core.challenge;

import java.time.Instant;

/**
 * 챌린지 목록 한 행의 투영 (어드민 명세 §3.7.1) — {@link ChallengeRepository#search}.
 *
 * <p>엔티티를 페이지로 끌어오지 않는다. 목록이 필요로 하는 아티스트 이름·곡·슬롯 수·
 * 편집본 수는 전부 다른 테이블이라, 엔티티를 읽으면 행마다 조회가 따라붙는다
 * (20행이면 40번). 조인과 서브쿼리로 <b>한 번에</b> 필요한 값만 꺼낸다.
 *
 * <p>{@code editedCount} 는 0019 이후 <b>편집본이 올라온 슬롯 수</b>다 — 조합 편집본
 * 테이블이 없어져 같은 테이블을 한 번 더 세는 모양이 됐다. {@code slotCount} 와 같으면
 * 전부 채워졌다는 뜻이고, 그래서 둘은 이제 상한이 같다.
 *
 * <p><b>전부 래퍼 타입이다</b> — JPQL 생성자 표현식은 박싱된 값을 그대로 넘기므로
 * 여기서 원시 타입을 쓰면 생성자 매칭이 런타임에 어긋날 수 있다. 원시 타입 변환은
 * 응답 DTO 로 옮기는 쪽에서 한다.
 */
public record ChallengeListRow(
        Integer challengeSeq,
        Integer artistSeq,
        String artistName,
        Integer songSeq,
        String songTitle,
        String singerName,
        String thumbnailKey,
        Integer durationMs,
        Long slotCount,
        Long editedCount,
        Long viewCount,
        Integer participantCount,
        Integer likeCount,
        Integer commentCount,
        Boolean deleted,
        Instant regDttm
) {
}

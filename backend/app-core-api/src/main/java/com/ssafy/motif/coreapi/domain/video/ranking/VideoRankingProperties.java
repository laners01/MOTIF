package com.ssafy.motif.coreapi.domain.video.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * HOT 참여 영상 랭킹 설정 (API 명세 §6.1).
 *
 * <p><b>왜 설정인가.</b> 원본 명세는 §6.1 의 API 계약과 Redis 키 이름만 적고 점수
 * 산출·갱신 주기를 정하지 않았다. 여기 수치는 전부 S15P21D104-113 에서 정한
 * <b>기본안</b>이라 근거가 명세에 없다 — 코드에 박으면 조정할 때마다 재배포가 된다.
 *
 * <p><b>가중치가 없다.</b> 점수가 {@code view_count × 0.5^(age/H)} 한 항이라 조회수에
 * 곱하는 상수는 모든 영상에 같이 곱해져 순위를 바꾸지 못한다. §5.1 아티스트 랭킹은
 * 조회·참여 <b>두 항의 비율</b>이 순위를 바꾸므로 그쪽에는 가중치가 있다. 조정해도
 * 아무 일이 일어나지 않는 설정을 두지 않는다.
 *
 * <p>prefix 를 {@code motif.ranking} 아래에 두지 않은 것은 §5.1 의
 * {@code RankingProperties} 를 건드리지 않기 위해서다 — 병렬 브랜치가 같은 파일에
 * 칸을 더하면 리베이스에서 되돌아온다.
 *
 * @param halfLife 감쇠 반감기 — 이만큼 지난 영상의 조회수는 절반으로 친다
 * @param interval 스냅샷 갱신 주기. §1.6 이 홈 캐시 TTL 5분을 "랭킹 배치 주기와 동일"
 *                 로 묶어 놨다
 * @param retain   스냅샷 버전 보존 기간 = <b>커서 시퀀스의 유효 기간</b>이다. 이보다
 *                 오래된 커서로 요청하면 빈 목록이 된다. {@code interval} 보다 넉넉해야
 *                 진행 중인 스크롤이 배치 한 틱에 끊기지 않는다
 */
@ConfigurationProperties(prefix = "motif.hot-video.ranking")
public record VideoRankingProperties(Duration halfLife, Duration interval, Duration retain) {
}

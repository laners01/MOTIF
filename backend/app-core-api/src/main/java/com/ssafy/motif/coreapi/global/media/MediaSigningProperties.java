package com.ssafy.motif.coreapi.global.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * CloudFront signed URL 설정 (API 명세 §1.7).
 *
 * <p><b>만료가 셋으로 갈려 있다.</b> 가르는 기준은 대상의 종류가 아니라 <b>만료됐을 때
 * 복구할 경로가 있는가</b>다 — 있으면 짧게, 없으면 목록이 살아 있는 만큼.
 *
 * @param keyPairId    CloudFront 공개키 ID. <b>비밀이 아니다</b> — 서명된 URL 의 쿼리에
 *                     그대로 실려 나간다. 그래서 기본값을 둔다
 * @param privateKey   공개키와 짝인 RSA 개인키(PEM). <b>비밀이고 fallback 이 없다</b> —
 *                     이 값이 새면 전 보호 콘텐츠의 서명을 위조할 수 있다.
 *                     로컬·CI 를 위해 빈 값으로 기동은 되지만, 그 상태에서 서명을
 *                     요청하면 {@link CloudFrontUrlSigner} 가 던진다(fail-closed)
 * @param playlistTtl  <b>HLS 프리픽스 서명</b>(재생목록·조각)의 만료. <b>사후 복구가
 *                     성립하지 않는다</b> — 자동재생에서는 403 을 만난 뒤 재발급을
 *                     받아 재시도하는 왕복이 카드마다 보이는 스톨이 되고, 서명 범위가
 *                     영상 하나라 재발급도 영상당 1회다(ADR-0012). 그래서 목록이 살아
 *                     있는 최대 구간에 맞춘다 — 커서 시퀀스가 죽는 시점
 *                     ({@code motif.view.video-ranking.retain}) 이후에는 재생할 목록
 *                     자체가 없다. 두 값은 같은 것을 재므로 한쪽만 바꾸면 다시
 *                     어긋난다. 기본 2시간 (ADR-0017)
 * @param videoFileTtl <b>단일 영상 파일</b>의 만료 — §7.6 편집본. 여기는 복구 경로가
 *                     실제로 있다(만료되면 앱이 §7.6 을 다시 부르고 새 URL 을 받는다).
 *                     재생목록과 같이 늘릴 이유가 없어 갈라 둔다. 기본 10분
 * @param imageTtl     <b>이미지</b>(썸네일·프로필)의 만료. 여기에는 그 복구 경로가
 *                     <b>아예 없다</b> — {@code <img>} 가 403 을 받으면 깨진 채로
 *                     남는다. 10분을 쓰면 마이페이지를 열어둔 채 잠깐 다른 앱을 보고
 *                     돌아왔을 때 3×3 썸네일이 통째로 깨진다. 길게 줘도 노출 범위는
 *                     서명된 그 한 장뿐이다(URL 단위 서명). 기본 6시간
 */
@ConfigurationProperties(prefix = "motif.media.signing")
public record MediaSigningProperties(String keyPairId, String privateKey,
                                     Duration playlistTtl, Duration videoFileTtl,
                                     Duration imageTtl) {
}

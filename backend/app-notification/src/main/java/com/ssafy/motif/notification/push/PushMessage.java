package com.ssafy.motif.notification.push;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FCM data-only 페이로드 (API 명세 §14.3).
 *
 * <p><b>notification 블록을 쓰지 않는다.</b> 시스템 알림 표시 여부는 앱이 로컬 설정을
 * 보고 정하는데(§14.3 — 서버는 사용자별 ON/OFF 를 알지 못한다), notification 블록을
 * 실으면 앱이 백그라운드일 때 <b>OS 가 먼저 트레이에 띄운다.</b> 그러면 알림을 끈
 * 사용자에게도 뜬다 — 클라이언트 필터링이 통째로 무력해진다.
 *
 * <p>값은 전부 문자열이다. FCM data 는 {@code Map<String,String>} 만 받는다.
 *
 * @param type    {@code NEW_CHALLENGE} | {@code NEW_NOTICE} — 앱이 딥링크를 고르는 키
 * @param targetKey 대상 식별자의 필드명({@code challengeSeq}·{@code noticeSeq}). §14.3 이
 *                  "target 식별자" 를 타입마다 다른 이름으로 싣기로 했다
 */
public record PushMessage(String type, String title, String body,
                          String targetKey, Object targetValue) {

    public static final String TYPE_NEW_CHALLENGE = "NEW_CHALLENGE";
    public static final String TYPE_NEW_NOTICE = "NEW_NOTICE";

    /** §3.7.2 컨슈머 동작 — 어드민 명세 §4 표가 문구까지 고정했다. */
    public static PushMessage newChallenge(String artistName, String songTitle, Integer challengeSeq) {
        return new PushMessage(TYPE_NEW_CHALLENGE,
                artistName + "의 새 챌린지", songTitle, "challengeSeq", challengeSeq);
    }

    /** §3.10.2 컨슈머 동작. body 는 발행자가 이미 60자로 자른 미리보기다. */
    public static PushMessage newNotice(String artistName, String contentPreview, Integer noticeSeq) {
        return new PushMessage(TYPE_NEW_NOTICE,
                artistName + " 공지", contentPreview, "noticeSeq", noticeSeq);
    }

    /**
     * {@code sentDttm} 은 보내는 시점에 찍는다 — 이벤트 발생 시각이 아니다. 재전달·DLQ
     * 재처리로 한참 뒤에 나갈 수 있고, 앱은 이 값으로 로컬 목록을 정렬한다.
     */
    public Map<String, String> data() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("title", title == null ? "" : title);
        data.put("body", body == null ? "" : body);
        data.put("sentDttm", Instant.now().toString());
        data.put(targetKey, String.valueOf(targetValue));
        return data;
    }
}

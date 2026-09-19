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
 * @param targetKey 대상 식별자의 필드명({@code challengeSeq}·{@code noticeSeq}·
 *                  {@code jobUuid}). §14.3 이 "target 식별자" 를 타입마다 다른 이름으로
 *                  싣기로 했다
 * @param extra     그 타입에만 있는 data 키. §14.3 골격에 <b>더해지는</b> 값이라 additive
 *                  다 — 모르는 키를 만난 구버전 앱은 무시한다
 */
public record PushMessage(String type, String title, String body,
                          String targetKey, Object targetValue, Long commentSeq,
                          Map<String, String> extra) {

    public static final String TYPE_NEW_CHALLENGE = "NEW_CHALLENGE";
    public static final String TYPE_NEW_NOTICE = "NEW_NOTICE";
    public static final String TYPE_NEW_COMMENT = "NEW_COMMENT";
    public static final String TYPE_NEW_REPLY = "NEW_REPLY";
    public static final String TYPE_COMPOSITE_DONE = "COMPOSITE_DONE";
    public static final String TYPE_COMPOSITE_FAILED = "COMPOSITE_FAILED";

    /** 추가 키가 없는 알림용 — 기존 팩터리 넷이 그대로 쓴다. */
    public PushMessage(String type, String title, String body,
                       String targetKey, Object targetValue, Long commentSeq) {
        this(type, title, body, targetKey, targetValue, commentSeq, Map.of());
    }

    /** 게시물 종류 → §14.3 대상 식별자 이름. 이벤트의 {@code targetType} 을 그대로 받는다. */
    private static final Map<String, String> TARGET_KEY = Map.of(
            "CHALLENGE", "challengeSeq",
            "VIDEO", "videoSeq",
            "NOTICE", "noticeSeq");

    /** §3.7.2 컨슈머 동작 — 어드민 명세 §4 표가 문구까지 고정했다. */
    public static PushMessage newChallenge(String artistName, String songTitle, Integer challengeSeq) {
        return new PushMessage(TYPE_NEW_CHALLENGE,
                artistName + "의 새 챌린지", songTitle, "challengeSeq", challengeSeq, null);
    }

    /** §3.10.2 컨슈머 동작. body 는 발행자가 이미 60자로 자른 미리보기다. */
    public static PushMessage newNotice(String artistName, String contentPreview, Integer noticeSeq) {
        return new PushMessage(TYPE_NEW_NOTICE,
                artistName + " 공지", contentPreview, "noticeSeq", noticeSeq, null);
    }

    /**
     * §9.3 댓글 — 게시물 소유자에게. 제목 문구는 <b>(기본안)</b> 이다(명세가 고정하지 않았다).
     *
     * @param targetType 이벤트의 {@code CHALLENGE}·{@code VIDEO}·{@code NOTICE}
     */
    public static PushMessage newComment(String targetType, Long targetSeq, Long commentSeq,
                                         String actorNickname, String contentPreview) {
        return new PushMessage(TYPE_NEW_COMMENT,
                actorNickname + "님이 댓글을 남겼어요", contentPreview,
                targetKey(targetType), targetSeq, commentSeq);
    }

    /** §9.3 답글 — 부모 댓글 작성자에게. */
    public static PushMessage newReply(String targetType, Long targetSeq, Long commentSeq,
                                       String actorNickname, String contentPreview) {
        return new PushMessage(TYPE_NEW_REPLY,
                actorNickname + "님이 답글을 남겼어요", contentPreview,
                targetKey(targetType), targetSeq, commentSeq);
    }

    /**
     * §13 합성 완료·실패 — 잡 주인에게 (작업명세 §4.6, S15P21D104-319).
     *
     * <p><b>대상 식별자는 {@code jobUuid} 다</b>(§14.3). 앱은 알림을 눌러 진입할 때
     * §13.3 을 <b>1회</b> 호출해 나머지를 받는다 — 결과 URL 도 {@code resultSeq} 도
     * 여기 싣지 않는다. 푸시는 큐에 머무를 수 있어 그 사이 값이 바뀔 수 있고,
     * 보호 등급 URL 은 서명 만료가 있다.
     *
     * <p><b>실패 본문을 서버가 고르지 않는다.</b> {@code failCode} 별 안내 문구는 앱이
     * 매핑하고(AI 서버 명세 §6), 그래야 "재촬영" 계열에서 재시도 버튼을 숨기는 판단이
     * 문구와 한곳에 있다. 서버는 짧은 공통 문구만 준다.
     *
     * <p>문구는 전부 <b>(기본안)</b> 이다 — 명세가 고정하지 않았다.
     *
     * @param jobType  {@code CHALLENGE} | {@code MOTIF} — 완료 본문이 갈린다
     * @param failCode 실패일 때만. 성공이면 {@code null} 이고 data 에 칸이 없다
     */
    public static PushMessage compositeFinished(String jobUuid, String jobType, String failCode) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("jobType", jobType);
        boolean failed = failCode != null;
        if (failed) {
            extra.put("failCode", failCode);
        }
        return new PushMessage(
                failed ? TYPE_COMPOSITE_FAILED : TYPE_COMPOSITE_DONE,
                failed ? "합성에 실패했어요" : "합성이 끝났어요",
                failed ? "다시 시도해 주세요" : doneBody(jobType),
                "jobUuid", jobUuid, null, extra);
    }

    /** 완료 후 갈 곳이 달라 본문도 갈린다 — CHALLENGE 는 게시(§8.8), MOTIF 는 보관함(§11.6). */
    private static String doneBody(String jobType) {
        return "MOTIF".equals(jobType) ? "보관함에 저장됐어요" : "결과를 확인하고 게시해 보세요";
    }

    /** 모르는 종류는 던진다 — 대상 키가 비면 앱이 어디로 갈지 알 수 없다. */
    private static String targetKey(String targetType) {
        String key = TARGET_KEY.get(targetType);
        if (key == null) {
            throw new IllegalArgumentException("알 수 없는 게시물 종류: " + targetType);
        }
        return key;
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
        // 댓글 알림만 싣는다 — 앱이 스레드 안 그 위치로 스크롤해야 한다(323).
        // §14.3 골격의 "대상 식별자" 에 더해지는 값이라 additive 다
        if (commentSeq != null) {
            data.put("commentSeq", String.valueOf(commentSeq));
        }
        data.putAll(extra);
        return data;
    }
}

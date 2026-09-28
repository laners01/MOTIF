package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 댓글·답글 작성 — core-api → {@code motif.topic} → app-notification (API 명세 §9.3 · ADMIN-11).
 *
 * <p><b>라우팅 키가 둘인데 계약은 하나다.</b> 루트 댓글이면 {@code comment.created},
 * 답글이면 {@code comment.replied} 로 나간다({@code parentCommentSeq} 로 가른다). 수신자를
 * 찾는 규칙만 다르고 싣는 값이 같아서 record 를 둘로 쪼개면 같은 필드를 두 곳에서
 * 고치게 된다. 키가 갈려 있는 이유는 컨슈머가 <b>알림 문구와 type 을 다르게</b> 쓰기
 * 때문이다(NEW_COMMENT · NEW_REPLY).
 *
 * <p><b>수신자를 발행측이 정해서 싣는다.</b> 알림 서비스는 {@code notif_app} 으로 붙고
 * {@code motif_core} 에는 {@code subscriptions} SELECT 권한 하나만 열려 있다(changeSet
 * 0003-03). 게시물 소유자나 부모 댓글 작성자를 알림 서비스가 직접 찾으려면 권한을
 * 네 테이블에 더 열어야 하는데, core-api 는 그 값을 이미 손에 들고 있다.
 *
 * <p><b>본인 알림은 발행 자체를 하지 않는다.</b> 작성자와 수신자가 같으면 relay 까지
 * 가지 않는다(ADMIN-11 자기 행위 규칙 — 아티스트가 자기 공지에 댓글을 달면 알림이 없다).
 * 컨슈머에서 거르면 "안 온 것" 과 "걸러진 것" 이 로그에서 구분되지 않는다.
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 *
 * @param eventId          이벤트 uuid — 컨슈머 멱등 키
 * @param occurredAt       발생 시각 (ISO-8601 UTC)
 * @param targetType       {@code CHALLENGE} · {@code VIDEO} · {@code NOTICE} — 컨슈머가
 *                         FCM 대상 식별자 이름을 이것으로 고른다(§14.3)
 * @param targetSeq        게시물 번호. 챌린지·공지는 int 범위지만 영상이 bigint 라 하나로 맞춘다
 * @param commentSeq       작성된 댓글·답글 번호
 * @param parentCommentSeq 루트 댓글이면 {@code null}. 값이 있으면 답글이다
 * @param recipientUserSeq 알림 받을 사람 — 루트 댓글은 게시물 소유자, 답글은 부모 댓글 작성자
 * @param actorNickname    알림 제목에 들어가는 작성자 이름({@code users.nickname})
 * @param contentPreview   알림 본문에 들어가는 앞 60자 — 공지 알림과 같은 길이다
 */
public record CommentCreatedEvent(
        String eventId,
        Instant occurredAt,
        String targetType,
        Long targetSeq,
        Long commentSeq,
        Long parentCommentSeq,
        Integer recipientUserSeq,
        String actorNickname,
        String contentPreview
) {

    /** 답글이면 {@code reply.created} 다 — relay 가 이걸로 라우팅 키를 고른다. */
    public boolean isReply() {
        return parentCommentSeq != null;
    }
}

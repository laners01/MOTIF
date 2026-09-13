package com.ssafy.motif.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 전용 에러 코드 — <b>어드민 명세 §5 표와 1:1</b>.
 *
 * <p><b>§5 를 한 번에 전량 등록한다.</b> 어드민 API 는 여러 세션이 병렬로 구현하는데,
 * 각자 자기 도메인 코드만 조금씩 추가하면 같은 enum 파일에서 계속 충돌한다.
 * 표가 이미 확정돼 있으므로 먼저 통째로 넣고, 이후 티켓은 <b>읽기만</b> 한다.
 * 여기에 없는 코드가 필요하면 명세 §5 에 먼저 추가한다(ErrorCode 규칙).
 *
 * <p>공통 코드({@code INVALID_PARAMETER}·{@code UNAUTHORIZED}·{@code FORBIDDEN}·
 * {@code NOT_FOUND}·{@code CONFLICT}·{@code INTERNAL_ERROR})는 {@link CommonErrorCode}
 * 가 소유한다 — code 전역 유일 규칙. 대행(§3.12)은 사용자 도메인 코드를 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    /** 3.1.1 — ID·PW 불일치. 어느 쪽이 틀렸는지 구분하지 않는다(계정 존재 노출 방지). */
    ADMIN_LOGIN_FAILED(401, "ADMIN_LOGIN_FAILED", "로그인 정보가 올바르지 않습니다."),

    /** 3.1.1 — {@code admins.is_active=FALSE}. */
    ADMIN_INACTIVE(403, "ADMIN_INACTIVE", "비활성화된 어드민 계정입니다."),

    /** 3.2 — kind 에 맞지 않는 Content-Type 또는 크기 상한 초과. */
    MEDIA_TYPE_NOT_ALLOWED(400, "MEDIA_TYPE_NOT_ALLOWED", "허용되지 않는 파일 형식이거나 크기를 초과했습니다."),

    /** 3.7·3.8 — 영상 키가 S3 에 없다(HEAD 실패). 이미지는 {@code IMAGE_NOT_UPLOADED}. */
    MEDIA_NOT_UPLOADED(409, "MEDIA_NOT_UPLOADED", "업로드되지 않은 파일입니다."),

    /**
     * 형식·방향·길이 위반 (§1.4 검증 2항). {@code data} 에 위반 사유 코드 배열을 싣는다.
     *
     * <p>값의 출처는 업로드 화면 JS 가 읽어 보낸 {@code durationMs}·{@code width}·
     * {@code height} 다 — core-api 는 ffmpeg 를 돌리지 않는다(v0.5 §1.4).
     */
    MEDIA_INVALID(400, "MEDIA_INVALID", "영상 규격이 올바르지 않습니다."),

    COMPANY_NOT_FOUND(404, "COMPANY_NOT_FOUND", "소속사를 찾을 수 없습니다."),
    GROUP_NOT_FOUND(404, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다."),
    SONG_NOT_FOUND(404, "SONG_NOT_FOUND", "곡을 찾을 수 없습니다."),
    COUNTRY_NOT_FOUND(404, "COUNTRY_NOT_FOUND", "국가를 찾을 수 없습니다."),
    BANNER_NOT_FOUND(404, "BANNER_NOT_FOUND", "배너를 찾을 수 없습니다."),
    REPORT_NOT_FOUND(404, "REPORT_NOT_FOUND", "신고를 찾을 수 없습니다."),
    BATCH_NOT_FOUND(404, "BATCH_NOT_FOUND", "배치를 찾을 수 없습니다."),

    /** 3.3.2/3 — 활성 회사명 중복({@code uq_companies_name_active}). */
    COMPANY_NAME_DUPLICATE(409, "COMPANY_NAME_DUPLICATE", "이미 등록된 소속사명입니다."),

    /** 3.3.4 — 비삭제 그룹·아티스트가 참조 중. {@code data} 에 참조 건수. */
    COMPANY_IN_USE(409, "COMPANY_IN_USE", "참조 중인 소속사는 삭제할 수 없습니다."),

    /** 3.4.4 — 비삭제 멤버 존재. */
    GROUP_IN_USE(409, "GROUP_IN_USE", "멤버가 있는 그룹은 삭제할 수 없습니다."),

    /** 3.5.4 — 그룹 소속 아티스트의 소속사는 그룹을 통해서만 바뀐다(DB §8). */
    ARTIST_COMPANY_LOCKED(409, "ARTIST_COMPANY_LOCKED", "그룹 소속 아티스트의 소속사는 직접 변경할 수 없습니다."),

    /** 3.5.5 — 이미 삭제된 아티스트. */
    ARTIST_ALREADY_DELETED(409, "ARTIST_ALREADY_DELETED", "이미 삭제된 아티스트입니다."),

    /** 3.7.2/5 — 슬롯·bbox·participant_mask 가 규칙에 맞지 않는다. */
    PARTICIPANT_INVALID(400, "PARTICIPANT_INVALID", "인물 슬롯 정보가 올바르지 않습니다."),

    /** 3.7.2/5 — 2ⁿ−1 전조합 편집본이 갖춰지지 않았다. {@code data} 에 누락 조합. */
    EDITED_SET_INCOMPLETE(409, "EDITED_SET_INCOMPLETE", "편집본 조합이 완결되지 않았습니다."),

    /** 3.7.2/5/6 — 편집본 길이가 원본과 다르다(§1.4 허용 오차 밖). */
    EDITED_DURATION_MISMATCH(409, "EDITED_DURATION_MISMATCH", "편집본 길이가 원본과 일치하지 않습니다."),

    /** 3.7.5 — 합성 잡이 참조 중인 편집본. */
    EDITED_IN_USE(409, "EDITED_IN_USE", "사용 중인 편집본은 삭제할 수 없습니다."),

    /** 3.8.6 / 3.9.2 — 가격이 없어 활성화할 수 없다. */
    PRICE_ACTIVATION_BLOCKED(409, "PRICE_ACTIVATION_BLOCKED", "가격이 없어 활성화할 수 없습니다."),

    /** 3.8.5 — 이미 같은 상태(§8 #15 — 멱등 대신 409). */
    ASSET_STATE_UNCHANGED(409, "ASSET_STATE_UNCHANGED", "이미 같은 상태입니다."),

    /** 3.11.2 — 배너 노출 순서 중복. */
    BANNER_ORDER_DUPLICATE(409, "BANNER_ORDER_DUPLICATE", "이미 사용 중인 노출 순서입니다."),

    /** 3.13.3 — 이미 인용·기각 처리된 신고. */
    REPORT_ALREADY_PROCESSED(409, "REPORT_ALREADY_PROCESSED", "이미 처리된 신고입니다."),

    USER_ALREADY_SUSPENDED(409, "USER_ALREADY_SUSPENDED", "이미 정지된 회원입니다."),
    USER_NOT_SUSPENDED(409, "USER_NOT_SUSPENDED", "정지 상태가 아닌 회원입니다."),

    /** 3.15.3 — 환불 가능 상태가 아니다. */
    ORDER_NOT_REFUNDABLE(409, "ORDER_NOT_REFUNDABLE", "환불할 수 없는 주문입니다."),

    /** 3.15.3 — PG 취소 호출 실패. 외부 의존 실패이므로 502. */
    PAYMENT_CANCEL_FAILED(502, "PAYMENT_CANCEL_FAILED", "결제 취소에 실패했습니다."),

    /** 3.16.4 — 같은 배치가 이미 실행 중. */
    BATCH_RUNNING(409, "BATCH_RUNNING", "이미 실행 중인 배치입니다.");

    private final int status;
    private final String code;
    private final String message;
}

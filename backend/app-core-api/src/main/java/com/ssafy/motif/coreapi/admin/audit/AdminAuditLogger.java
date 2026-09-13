package com.ssafy.motif.coreapi.admin.audit;

import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 어드민 감사 로그 (어드민 명세 §1.3) — S15P21D104-555.
 *
 * <p><b>왜 DB 가 아니라 로그인가</b> — 대행(좋아요·댓글)은 아티스트 유저로 행을 만든다.
 * {@code video_likes} 는 (대상, 유저, 시각) 3칸뿐이고 댓글 테이블에도 등록자 칸이 없어
 * <b>수행한 어드민을 DB 에 남길 자리가 없다</b>. {@code admin_action_logs} 테이블은
 * 도입하지 않기로 확정됐고(§6 #4), 대신 이 로그가 갈음한다.
 *
 * <p>출력은 두 갈래다 — 전용 JSON 파일({@code logback-spring.xml} 의 {@code ADMIN_AUDIT},
 * PVC 마운트로 파드 재시작에도 남는다)과 stdout(기존 CONSOLE). 관측 도구를 붙이기
 * 전까지는 파일이 원본이다(§1.3, 회의 확정 R1).
 *
 * <p>필드는 MDC 로 싣는다 — JSON 인코더가 구조화 필드로 뽑아 주므로 나중에 Loki 에서
 * 파싱 규칙 없이 질의할 수 있다. {@code requestId} 는 {@code RequestIdFilter} 가 이미
 * 넣어둔 값이라 여기서 다시 넣지 않는다(잡 하나를 API → 큐 → 워커까지 한 ID 로 추적).
 */
@Component
public class AdminAuditLogger {

    /** 전용 로거 이름 — {@code logback-spring.xml} 의 {@code <logger>} 와 짝이다. */
    private static final Logger AUDIT = LoggerFactory.getLogger("motif.admin.audit");

    /** 정상 처리. 실패는 에러 코드 문자열을 그대로 쓴다(§1.3 {@code result={OK|code}}). */
    public static final String RESULT_OK = "OK";

    /**
     * 대행 행위 (§1.3) — 행 자체는 아티스트 유저로 남으므로 수행자는 여기에만 남는다.
     *
     * @param action    {@code LIKE_VIDEO} · {@code CREATE_COMMENT} 등
     * @param artistSeq 대행한 아티스트
     * @param result    {@link #RESULT_OK} 또는 실패 에러 코드
     */
    public void actAs(AuthenticatedAdmin admin, int artistSeq, String action, long targetSeq, String result) {
        Map<String, String> fields = baseFields(admin, action, result);
        fields.put("artistSeq", String.valueOf(artistSeq));
        fields.put("targetSeq", String.valueOf(targetSeq));
        log(fields, "ADMIN_ACT_AS admin={} usr={} artist={} action={} target={} result={}",
                admin.adminSeq(), admin.userSeq(), artistSeq, action, targetSeq, result);
    }

    /**
     * 콘텐츠 등록·수정·삭제·신고 처리·회원 정지 등 일반 쓰기 (§1.3 첫 행).
     *
     * <p>감사 <b>컬럼</b>({@code reg_usr_seq} 등)은 서비스가 따로 채운다 — 이 로그는
     * 컬럼이 담지 못하는 "누가 언제 어떤 요청으로" 를 남긴다.
     *
     * @param targetType {@code CHALLENGE} · {@code ASSET} · {@code REPORT} 등
     */
    public void write(AuthenticatedAdmin admin, String action, String targetType, long targetSeq, String result) {
        Map<String, String> fields = baseFields(admin, action, result);
        fields.put("targetType", targetType);
        fields.put("targetSeq", String.valueOf(targetSeq));
        log(fields, "ADMIN_WRITE admin={} usr={} action={} target={}:{} result={}",
                admin.adminSeq(), admin.userSeq(), action, targetType, targetSeq, result);
    }

    private static Map<String, String> baseFields(AuthenticatedAdmin admin, String action, String result) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("adminSeq", String.valueOf(admin.adminSeq()));
        fields.put("userSeq", String.valueOf(admin.userSeq()));
        fields.put("action", action);
        fields.put("result", result);
        return fields;
    }

    /** MDC 는 요청 스레드에 남으면 다음 로그를 오염시킨다 — 넣은 키만 정확히 되돌린다. */
    private static void log(Map<String, String> fields, String format, Object... args) {
        fields.forEach(MDC::put);
        try {
            AUDIT.info(format, args);
        } finally {
            fields.keySet().forEach(MDC::remove);
        }
    }
}

package com.ssafy.motif.coreapi.global.exception;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * {@link ApiController} 마커가 붙은 컨트롤러만 감싸는 전역 핸들러
 * (스코프 제한 — MODERA 관례, ADR-0004). actuator·springdoc은 말려들지 않는다.
 */
@Slf4j
@RestControllerAdvice(annotations = ApiController.class)
public class GlobalExceptionHandler {

    /** 도메인 규칙 위반 — ErrorCode대로 응답. 상세가 있으면 {@code data} 에 싣는다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: code={} message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode, e.getData()));
    }

    /** {@code @Valid} 검증 실패 — 필드 오류 리스트를 data에 싣는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleValidation(MethodArgumentNotValidException e) {
        List<FieldErrorResponse> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        // 필드명만 남긴다 — 거부된 값(rejectedValue)에는 비밀번호·토큰이 실려 온다
        log.warn("검증 실패: {}", fieldErrors.stream().map(FieldErrorResponse::field).toList());
        return ResponseEntity.status(CommonErrorCode.INVALID_PARAMETER.getStatus())
                .body(ApiResponse.fail(CommonErrorCode.INVALID_PARAMETER, fieldErrors));
    }

    /** 본문 해석 불가·파라미터 타입 불일치 — INVALID_PARAMETER. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(Exception e) {
        log.warn("요청 해석 실패: {}", e.getMessage());
        return ResponseEntity.status(CommonErrorCode.INVALID_PARAMETER.getStatus())
                .body(ApiResponse.fail(CommonErrorCode.INVALID_PARAMETER));
    }

    /** 그 밖의 모든 예외 — 스택은 로그로만 남기고 응답에는 싣지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.fail(CommonErrorCode.INTERNAL_ERROR));
    }
}

package com.ssafy.motif.coreapi.global.security;

import com.ssafy.motif.coreapi.domain.auth.AuthErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 미인증 요청 → 401 {@code UNAUTHORIZED} (명세 §1.5). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("미인증 접근: {} {}", request.getMethod(), request.getRequestURI());
        errorWriter.write(response, AuthErrorCode.UNAUTHORIZED);
    }
}

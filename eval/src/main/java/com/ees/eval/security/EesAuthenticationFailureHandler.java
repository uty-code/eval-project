package com.ees.eval.security;

import com.ees.eval.exception.EmployeeRetiredException;
import com.ees.eval.service.LoginLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.LockedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Spring Security 인증 실패 시 커스텀 로직을 수행하는 핸들러입니다.
 * 실패 원인(비밀번호 불일치, 퇴사자 등)에 따라 쿼리 파라미터를 다르게 설정하여
 * 로그인 화면에서 명확한 안내 메시지를 보여줄 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EesAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginLogService loginLogService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        // 디버깅: 실제 예외 타입과 원인을 로그로 출력
        log.warn("[인증 실패] 예외 타입: {}, 메시지: {}", exception.getClass().getName(), exception.getMessage());
        if (exception.getCause() != null) {
            log.warn("[인증 실패] 원인 예외 타입: {}", exception.getCause().getClass().getName());
        }

        String errorMessage = "invalid"; // 기본값 (아이디 또는 비밀번호 불일치)

        // 예외 원인 체인 전체를 순회하여 퇴사자 예외 여부 확인
        // (Spring Security 버전에 따라 예외가 여러 단계로 래핑될 수 있음)
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof EmployeeRetiredException) {
                errorMessage = "retired"; // 퇴사자 계정
                break;
            } else if (cause instanceof LockedException) {
                errorMessage = "locked"; // 잠긴 계정
                break;
            } else if (cause instanceof org.springframework.security.authentication.DisabledException) {
                errorMessage = "pending"; // 승인 대기 계정
                break;
            }
            cause = cause.getCause();
        }
        
        // 로그인 실패 시 login_logs_51에 is_failure='y'로 기록됨
        // (LoginLogService.recordFailure에서 처리)


        // 로그인 이력 기록 (비동기)
        String logResultCode = switch (errorMessage) {
            case "retired"  -> "FAIL_RETIRED";
            case "locked"   -> "FAIL_LOCKED";
            case "pending"  -> "FAIL_PENDING";
            default         -> "FAIL_INVALID";
        };
        String loginInput = request.getParameter("username");
        loginLogService.recordFailure(loginInput != null ? loginInput : "", logResultCode, request);

        // 로그인 페이지로 에러 코드와 함께 리다이렉트
        String redirectUrl = "/login?error=" + errorMessage;

        setDefaultFailureUrl(redirectUrl);
        super.onAuthenticationFailure(request, response, exception);
    }
}

package com.ees.eval.security;

import com.ees.eval.service.LoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 로그인 성공 이벤트를 감지하여, 로그인 성공 이력을 기록합니다.
 * 계정 잠금 해제는 login_logs_51에 성공 기록(is_failure='n')이 남으므로
 * 별도의 카운터 초기화가 필요하지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final LoginLogService loginLogService;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            try {
                Long empId = Long.parseLong(username);
                // 로그인 성공 이력 기록 (is_failure='n'으로 저장됨)
                HttpServletRequest request = getRequest();
                if (request != null) {
                    loginLogService.recordSuccess(empId, request);
                }
            } catch (Exception e) {
                log.error("Failed to process login success for user: {}", username, e);
            }
        }
    }

    /**
     * 현재 스레드의 HttpServletRequest를 반환합니다.
     * Spring Security 인증은 요청 스레드에서 실행되므로 RequestContextHolder로 접근 가능합니다.
     */
    private HttpServletRequest getRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}

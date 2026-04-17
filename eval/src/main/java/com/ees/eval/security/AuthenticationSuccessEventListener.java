package com.ees.eval.security;

import com.ees.eval.mapper.EmployeeMapper;
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
 * 로그인 성공 이벤트를 감지하여, 로그인 실패 횟수를 0으로 초기화합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final EmployeeMapper employeeMapper;
    private final LoginLogService loginLogService;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            try {
                Long empId = Long.parseLong(username);
                // 실패 횟수 초기화
                employeeMapper.resetLoginFailCnt(empId);
                // 로그인 성공 이력 기록 (비동기)
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

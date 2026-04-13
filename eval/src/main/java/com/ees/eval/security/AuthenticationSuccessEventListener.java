package com.ees.eval.security;

import com.ees.eval.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 로그인 성공 이벤트를 감지하여, 로그인 실패 횟수를 0으로 초기화합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final EmployeeMapper employeeMapper;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            try {
                Long empId = Long.parseLong(username);
                employeeMapper.resetLoginFailCnt(empId);
            } catch (Exception e) {
                log.error("Failed to reset login fail count for user: {}", username, e);
            }
        }
    }
}

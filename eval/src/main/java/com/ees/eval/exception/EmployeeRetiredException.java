package com.ees.eval.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * 퇴사 처리된(is_deleted='y') 사원이 로그인을 시도할 때 발생하는 예외입니다.
 * Spring Security의 AuthenticationException을 상속받아 인증 실패 프로세스에서 처리됩니다.
 */
public class EmployeeRetiredException extends AuthenticationException {
    
    public EmployeeRetiredException(String msg) {
        super(msg);
    }

    public EmployeeRetiredException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

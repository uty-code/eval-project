package com.ees.eval.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security Context에서 현재 로그인한 사용자의 정보를 추출하는 유틸리티 클래스입니다.
 */
public class SecurityUtil {

    /**
     * 현재 로그인한 사원의 고유 식별자(empId)를 반환합니다.
     * 로그인하지 않았거나 인증 정보가 없는 경우 시스템 기본 ID(1L)를 반환합니다.
     *
     * @return 현재 로그인한 사원의 empId (Long)
     */
    public static Long getCurrentEmployeeId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null 
                || !authentication.isAuthenticated() 
                || authentication instanceof AnonymousAuthenticationToken) {
            return 1L; // 시스템 기본 관리자 ID
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            try {
                // CustomUserDetailsService에서 username을 empId 문자열로 설정함
                return Long.parseLong(userDetails.getUsername());
            } catch (NumberFormatException e) {
                return 1L;
            }
        }

        return 1L;
    }
}

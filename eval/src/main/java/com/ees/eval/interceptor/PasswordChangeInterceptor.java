package com.ees.eval.interceptor;

import com.ees.eval.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 최초 로그인 시 비밀번호 변경을 강제하는 인터셉터입니다.
 * CustomUserDetails의 pwdChangeRequired 플래그가 'y'인 경우,
 * 비밀번호 변경 페이지(/settings/profile)로 리다이렉트합니다.
 * ROLE_ADMIN 권한을 가진 사용자는 강제 변경 대상에서 제외됩니다.
 */
@Slf4j
public class PasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUserDetails user) {
            
            // ROLE_ADMIN은 강제 비밀번호 변경 대상에서 제외
            boolean isAdmin = user.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
            if (isAdmin) {
                return true;
            }

            // 비밀번호 변경이 필요한 상태인지 확인
            if (user.isPwdChangeRequired()) {
                String uri = request.getRequestURI();
                
                // 비밀번호 변경 페이지, 정적 리소스, 로그아웃 요청 등은 허용
                if (uri.startsWith("/settings") || 
                    uri.startsWith("/logout") || 
                    uri.startsWith("/css/") || 
                    uri.startsWith("/js/") || 
                    uri.startsWith("/img/") || 
                    uri.startsWith("/lib/") ||
                    uri.equals("/favicon.ico")) {
                    return true;
                }

                log.info("[PasswordChangeInterceptor] 비밀번호 변경 필요 - 사용자: {}, 요청 URI: {}", user.getUsername(), uri);

                // HTMX 요청인 경우 HX-Redirect 헤더를 사용하여 전체 페이지 리다이렉트 유도
                if (request.getHeader("HX-Request") != null) {
                    response.setHeader("HX-Redirect", "/settings/profile");
                    // HTMX 요청에 대해서는 빈 응답을 보내거나 200 OK를 보내되 바디를 비움
                    response.setStatus(HttpServletResponse.SC_OK);
                    return false;
                }

                // 일반 요청인 경우 표준 리다이렉트 수행
                response.sendRedirect("/settings/profile");
                return false;
            }
        }

        return true;
    }
}


package com.ees.eval.security;

import com.ees.eval.security.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인 성공 후 처리를 담당하는 핸들러입니다.
 * 사번과 비밀번호가 동일한 경우(초기 비밀번호 상태)를 감지하여
 * 비밀번호 변경 페이지로 강제 리다이렉트합니다.
 * 
 * BCrypt 연산 없이 원본 비밀번호와 사번의 단순 문자열 비교로 처리하여
 * 로그인 성능에 영향을 주지 않습니다. (0ms vs BCrypt ~100ms)
 * ROLE_ADMIN은 강제 변경 대상에서 제외됩니다.
 */
@Slf4j
@Component
public class EesAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public EesAuthenticationSuccessHandler() {
        // 기본 성공 URL 설정
        setDefaultTargetUrl("/dashboard");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof CustomUserDetails user) {
            String username = user.getUsername(); // 사번
            String rawPassword = request.getParameter("password"); // 사용자가 입력한 원본 비밀번호

            // ROLE_ADMIN은 강제 변경 대상에서 제외
            boolean isAdmin = user.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));

            // 원본 비밀번호와 사번이 동일하면 → 비밀번호 변경 필요 상태로 세팅
            if (!isAdmin && rawPassword != null && rawPassword.equals(username)) {
                log.info("[SuccessHandler] 초기 비밀번호 감지 - 사번: {}, 비밀번호 변경 필요", username);

                // CustomUserDetails의 pwdChangeRequired를 'y'로 갱신
                CustomUserDetails updatedUser = new CustomUserDetails(
                        user.getUsername(),
                        user.getPassword(),
                        user.isEnabled(),
                        user.isAccountNonExpired(),
                        user.isCredentialsNonExpired(),
                        user.isAccountNonLocked(),
                        user.getAuthorities(),
                        "y"
                );
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                updatedUser, null, updatedUser.getAuthorities()
                        )
                );

                // 비밀번호 변경 페이지로 리다이렉트
                getRedirectStrategy().sendRedirect(request, response, "/settings/profile");
                return;
            }
        }

        // 정상 로그인 → 기본 동작 (대시보드로 이동)
        super.onAuthenticationSuccess(request, response, authentication);
    }
}

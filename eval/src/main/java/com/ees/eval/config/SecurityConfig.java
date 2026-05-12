package com.ees.eval.config;

import com.ees.eval.security.CustomAccessDeniedHandler;
import com.ees.eval.security.CustomAuthenticationEntryPoint;
import com.ees.eval.security.EesAuthenticationFailureHandler;
import com.ees.eval.security.EesAuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 관련 정책 및 빈(Bean) 등록을 담당하는 설정 클래스입니다.
 * HTTP 접근 제어 설정을 정의합니다.
 * PasswordEncoder 빈은 PasswordEncoderConfig에서 별도 관리됩니다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final EesAuthenticationFailureHandler authenticationFailureHandler;
    private final EesAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    /**
     * HTTP 보안 필터 체인을 설정합니다. 
     * 초기 개발 단계에서는 편의를 위해 모든 요청을 허용(permitAll)하도록 구성되어 있습니다.
     *
     * @param http HttpSecurity 설정 객체
     * @return 설정이 완료된 SecurityFilterChain
     * @throws Exception 설정 오류 시 발생
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // 초기 개발 단계에서는 CSRF 비활성화
            .authorizeHttpRequests(auth -> auth
                // 정적 리소스(favicon.ico, css, js 등)는 누구나 접근 가능
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico", "/webjars/**").permitAll()
                // 추가적인 공용 페이지 허용
                .requestMatchers("/login", "/register", "/error-page/**").permitAll()
                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
            )
            .formLogin(form -> form
                .loginPage("/login")             // 커스텀 로그인 페이지 경로
                .successHandler(authenticationSuccessHandler) // 로그인 성공 시 비밀번호 변경 필요 여부 체크
                .failureHandler(authenticationFailureHandler) // 커스텀 실패 처리기 등록
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
        
        return http.build();
    }
}


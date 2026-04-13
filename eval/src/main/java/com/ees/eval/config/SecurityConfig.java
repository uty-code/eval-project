package com.ees.eval.config;

import com.ees.eval.security.EesAuthenticationFailureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 관련 정책 및 빈(Bean) 등록을 담당하는 설정 클래스입니다.
 * 암호화 방식(BCrypt) 및 HTTP 접근 제어 설정을 정의합니다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final EesAuthenticationFailureHandler authenticationFailureHandler;

    /**
     * 사원 패스워드 암호화를 위한 BCrypt 인코더를 빈으로 등록합니다.
     * 강도 높은 해시 알고리즘을 사용하여 보안성을 확보합니다.
     *
     * @return BCryptPasswordEncoder 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
                        // 정적 리소스 및 로그인 페이지는 누구나 접근 가능
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login") // 커스텀 로그인 페이지 경로
                        .defaultSuccessUrl("/dashboard", true) // 로그인 성공 시 이동할 기본 경로
                        .failureHandler(authenticationFailureHandler) // 커스텀 실패 처리기 등록
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());

        return http.build();
    }
}

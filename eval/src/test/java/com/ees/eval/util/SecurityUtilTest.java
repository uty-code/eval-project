package com.ees.eval.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("성공: 인증된 사용자의 empId를 정확히 반환해야 한다")
    void getCurrentEmployeeId_WithAuthenticatedUser_ReturnsEmpId() {
        // given
        UserDetails userDetails = new User("1001", "password", Collections.emptyList());
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // when
        Long empId = SecurityUtil.getCurrentEmployeeId();

        // then
        assertThat(empId).isEqualTo(1001L);
    }

    @Test
    @DisplayName("성공: 인증 정보가 없는 경우 기본값 1L을 반환해야 한다")
    void getCurrentEmployeeId_WithNoAuthentication_ReturnsDefaultId() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Long empId = SecurityUtil.getCurrentEmployeeId();

        // then
        assertThat(empId).isEqualTo(1L);
    }

    @Test
    @DisplayName("성공: 익명 사용자인 경우 기본값 1L을 반환해야 한다")
    void getCurrentEmployeeId_WithAnonymousUser_ReturnsDefaultId() {
        // given
        Authentication auth = new AnonymousAuthenticationToken("key", "anonymousUser", 
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // when
        Long empId = SecurityUtil.getCurrentEmployeeId();

        // then
        assertThat(empId).isEqualTo(1L);
    }

    @Test
    @DisplayName("성공: 사번이 숫자 형식이 아닌 경우 기본값 1L을 반환해야 한다")
    void getCurrentEmployeeId_WithInvalidUsernameFormat_ReturnsDefaultId() {
        // given
        UserDetails userDetails = new User("admin", "password", Collections.emptyList());
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // when
        Long empId = SecurityUtil.getCurrentEmployeeId();

        // then
        assertThat(empId).isEqualTo(1L);
    }

    @Test
    @DisplayName("성공: Principal이 UserDetails가 아닌 경우 기본값 1L을 반환해야 한다")
    void getCurrentEmployeeId_WithInvalidPrincipalType_ReturnsDefaultId() {
        // given
        Authentication auth = new UsernamePasswordAuthenticationToken("just-a-string", null);
        
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // when
        Long empId = SecurityUtil.getCurrentEmployeeId();

        // then
        assertThat(empId).isEqualTo(1L);
    }

    @Test
    @DisplayName("유틸리티 클래스 제약: 프라이빗 생성자와 final 클래스여야 한다")
    void utilityClassConstraints() throws NoSuchMethodException {
        // Class check
        assertThat(Modifier.isFinal(SecurityUtil.class.getModifiers())).isTrue();

        // Constructor check
        Constructor<SecurityUtil> constructor = SecurityUtil.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        
        try {
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(e.getCause().getMessage()).isEqualTo("Utility class cannot be instantiated");
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("동시성: 서로 다른 스레드에서 각자의 인증 정보를 유지해야 한다")
    void getCurrentEmployeeId_ThreadIsolation() throws ExecutionException, InterruptedException {
        // given
        CompletableFuture<Long> thread1 = CompletableFuture.supplyAsync(() -> {
            UserDetails user1 = new User("1001", "p1", Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user1, null, user1.getAuthorities()));
            return SecurityUtil.getCurrentEmployeeId();
        });

        CompletableFuture<Long> thread2 = CompletableFuture.supplyAsync(() -> {
            UserDetails user2 = new User("2002", "p2", Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user2, null, user2.getAuthorities()));
            return SecurityUtil.getCurrentEmployeeId();
        });

        // when & then
        assertThat(thread1.get()).isEqualTo(1001L);
        assertThat(thread2.get()).isEqualTo(2002L);
    }
}

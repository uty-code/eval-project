package com.ees.eval.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Spring Security의 표준 User 객체를 확장하여
 * 사원의 추가 정보(비밀번호 변경 필요 여부 등)를 담는 커스텀 UserDetails 클래스입니다.
 */
@Getter
public class CustomUserDetails extends User {

    private final String pwdChangeRequired;

    public CustomUserDetails(String username, String password, boolean enabled, boolean accountNonExpired,
                             boolean credentialsNonExpired, boolean accountNonLocked,
                             Collection<? extends GrantedAuthority> authorities, String pwdChangeRequired) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.pwdChangeRequired = pwdChangeRequired;
    }

    public boolean isPwdChangeRequired() {
        return "y".equalsIgnoreCase(pwdChangeRequired);
    }
}

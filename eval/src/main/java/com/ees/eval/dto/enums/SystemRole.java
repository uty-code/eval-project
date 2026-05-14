package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 시스템 내 사용자 권한(Role)을 정의하는 Enum입니다.
 * ROLE_ADMIN: 관리자, ROLE_EXECUTIVE: 임원, ROLE_USER: 일반 사용자
 */
@Getter
@RequiredArgsConstructor
public enum SystemRole {
    /** 시스템 관리자 */
    ROLE_ADMIN("ROLE_ADMIN"),
    
    /** 임원 (최종 평가자) */
    ROLE_EXECUTIVE("ROLE_EXECUTIVE"),
    
    /** 일반 사원 */
    ROLE_USER("ROLE_USER");

    private final String code;

    public static SystemRole fromCode(String code) {
        for (SystemRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        return null;
    }
}

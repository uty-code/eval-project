package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 평가 가중치 적용 대상 역할을 정의하는 Enum입니다.
 * LEADER: 부서장, STAFF: 일반 사원
 */
@Getter
@RequiredArgsConstructor
public enum WeightTargetRole {
    /** 부서장 */
    LEADER("LEADER"),
    
    /** 일반 사원 */
    STAFF("STAFF");

    private final String code;

    public static WeightTargetRole fromCode(String code) {
        for (WeightTargetRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        return null;
    }
}

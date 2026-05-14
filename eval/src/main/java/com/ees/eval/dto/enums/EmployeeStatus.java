package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사원의 재직 상태를 정의하는 Enum입니다.
 * EMPLOYED: 재직, RETIRED: 퇴사, PENDING: 승인대기
 */
@Getter
@RequiredArgsConstructor
public enum EmployeeStatus {
    /** 재직 */
    EMPLOYED("EMPLOYED"),
    
    /** 퇴사 */
    RETIRED("RETIRED"),
    
    /** 승인 대기 */
    PENDING("PENDING");

    private final String code;

    public static EmployeeStatus fromCode(String code) {
        for (EmployeeStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}

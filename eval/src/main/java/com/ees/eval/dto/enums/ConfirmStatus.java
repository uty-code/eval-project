package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 평가 데이터의 확정 상태를 정의하는 Enum입니다.
 * WAITING: 대기/임시저장, SUBMITTED: 제출완료
 */
@Getter
@RequiredArgsConstructor
public enum ConfirmStatus {
    /** 임시저장/대기 */
    WAITING("WAITING"),
    
    /** 제출완료 */
    SUBMITTED("SUBMITTED");

    private final String code;

    /**
     * 코드 값으로 Enum 상수를 찾습니다.
     * @param code 코드 값
     * @return 매칭되는 Enum 상수, 없으면 null
     */
    public static ConfirmStatus fromCode(String code) {
        for (ConfirmStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}

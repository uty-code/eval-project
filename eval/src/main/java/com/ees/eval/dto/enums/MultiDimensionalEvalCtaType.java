package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 다면평가 사용자 액션(버튼) 유형을 관리하는 Enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum MultiDimensionalEvalCtaType {
    EDIT("작성"),
    VIEW("조회"),
    LOCKED("잠김"),
    WAITING_SELF("자가평가 대기");

    private final String description;
}

package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 자가평가 목록에서 보여줄 액션 버튼 유형을 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum MyEvaluationCtaType {
    EDIT("작성하기", "btn-primary"),
    VIEW("조회하기", "btn-outline-primary"),
    LOCKED("잠김(조회)", "btn-secondary");

    private final String description;
    private final String btnClass;
}

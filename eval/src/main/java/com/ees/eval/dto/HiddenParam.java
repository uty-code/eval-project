package com.ees.eval.dto;

import lombok.Builder;

/**
 * HTML Form 제출 시 함께 전달되어야 하는 숨김 파라미터(hidden input)를 표현하는 불변 데이터 전송 객체(Record)입니다.
 * FilterOption과 시맨틱을 명확히 분리하기 위해 별도로 선언되었습니다.
 *
 * @param name hidden input의 name 속성으로 사용될 파라미터명
 * @param value hidden input의 value 속성으로 사용될 값
 */
@Builder
public record HiddenParam(
    String name,
    String value
) {}

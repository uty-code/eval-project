package com.ees.eval.dto;

import lombok.Builder;

/**
 * 셀렉트 박스나 라디오 버튼 등의 필터 옵션을 타입 안정적으로 표현하는 불변 데이터 전송 객체(Record)입니다.
 *
 * @param value 실제 백엔드로 전달될 상태 코드나 값
 * @param label 화면상에 사용자에게 표시될 한글 또는 다국어 명칭
 */
@Builder
public record FilterOption(
    String value,
    String label
) {}

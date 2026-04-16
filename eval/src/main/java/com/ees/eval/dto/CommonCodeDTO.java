package com.ees.eval.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 공통 코드 정보를 관리하기 위한 데이터 전송 객체(Record)입니다.
 * Java 21의 record 타입을 사용하여 구조를 간결화했습니다.
 *
 * @param codeId      코드 고유 식별자
 * @param groupCode   코드 그룹 구분자
 * @param codeValue   코드 실제 값
 * @param codeName    코드 명칭
 * @param description 코드 설명
 * @param isDeleted   삭제 여부
 * @param version     데이터 수정 버전 (낙관적 락 활용)
 * @param createdAt   생성 일시
 * @param createdBy   생성자 ID
 * @param updatedAt   수정 일시
 * @param updatedBy   수정자 ID
 */
@Builder
public record CommonCodeDTO(
        Long codeId,
        String groupCode,
        String codeValue,
        String codeName,
        String description,
        String isDeleted,
        Integer version,
        LocalDateTime createdAt,
        Long createdBy,
        LocalDateTime updatedAt,
        Long updatedBy) {
}

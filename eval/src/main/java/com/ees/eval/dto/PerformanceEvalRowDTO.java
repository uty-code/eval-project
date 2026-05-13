package com.ees.eval.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 성과/역량 평가 리스트의 테이블 행 데이터를 담는 DTO
 */
@Builder
public record PerformanceEvalRowDTO(
    Long mappingId,
    Long evaluateeId,
    String deptName,
    String positionName,
    String titleName,
    Long empId, // 사번
    String name,
    
    // MBO (성과평가)
    BigDecimal selfPerfScore,
    BigDecimal managerPerfScore,
    
    // COMP (역량평가)
    BigDecimal selfCompScore,
    BigDecimal managerCompScore,
    
    // 상태 및 제어
    String evalStatus, // "완료", "진행중", "대기", "미배정"
    String ctaStatus,  // "PRIMARY", "LOCKED", "WAITING", "WEIGHT_ERROR"
    
    // 검색용 데이터 유지 (옵션)
    Long deptId
) {}

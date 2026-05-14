package com.ees.eval.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 최종 등급 확정 목록에서 개별 평가 대상자의 상태를 나타내는 DTO입니다.
 * N+1 쿼리 방지를 위해 모든 필요 플래그를 미리 계산하여 담습니다.
 */
@Builder
public record FinalGradeTaskDTO(
        Long mappingId,
        Long periodId,
        String periodName,
        Integer periodYear,
        Long evaluateeId,
        String evaluateeName,
        String deptName,
        String titleName,       // 직급
        Long empId,             // 사번

        // ── 일반사원용: 성과평가 (MBO) ──
        BigDecimal selfPerfScore,       // 자가평가
        BigDecimal managerPerfScore,    // 1차평가
        BigDecimal executivePerfScore,  // 2차평가

        // ── 일반사원용: 역량평가 (COMP) ──
        BigDecimal selfCompScore,       // 자가평가
        BigDecimal managerCompScore,    // 1차평가
        BigDecimal executiveCompScore,  // 2차평가

        // ── 부서장용: 다면평가 (MULTI_DIMENSIONAL) ──
        BigDecimal selfMultiScore,      // 자가평가
        BigDecimal managerMultiScore,   // 1차평가
        BigDecimal executiveMultiScore, // 2차평가

        // 예상 등급 및 종합 점수
        String expectedGrade,
        Integer totalScore,

        boolean allSubmitted,
        boolean selfSubmitted,
        boolean weightValid,
        boolean isLeader,
        Long deptId
) {
}

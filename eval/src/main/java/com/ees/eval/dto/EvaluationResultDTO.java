package com.ees.eval.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 평가 결과 현황 화면에서 사용하는 DTO입니다.
 * 피평가자의 유형별 1차/2차/최종 점수, 상태, 종합 점수 및 등급을 포함합니다.
 *
 * <p>탭별 사용 필드:</p>
 * <ul>
 *     <li>일반사원 탭: mbo*, comp* + 종합결과</li>
 *     <li>부서장 탭: multi* + 종합결과</li>
 * </ul>
 *
 * @param empId             사원 ID (사번으로도 사용)
 * @param empName           사원명
 * @param deptName          부서명
 * @param positionName      직위명 (대리, 사원 등)
 * @param jobTitle          직책명 (부서장/팀원)
 * @param isLeader          부서장 여부 (탭 분류용)
 * @param mbo1stScore       성과/업무 1차 점수 (MANAGER 매핑)
 * @param mbo2ndScore       성과/업무 2차 점수 (EXECUTIVE 매핑)
 * @param mboFinalScore     성과/업무 최종 점수 (= 2차)
 * @param mboStatus         성과/업무 평가 진행 상태
 * @param comp1stScore      역량 1차 점수 (MANAGER 매핑)
 * @param comp2ndScore      역량 2차 점수 (EXECUTIVE 매핑)
 * @param compFinalScore    역량 최종 점수 (= 2차)
 * @param compStatus        역량 평가 진행 상태
 * @param multi1stScore     다면 1차 점수 (SUBORDINATE 매핑 평균)
 * @param multi2ndScore     다면 2차 점수 (EXECUTIVE 매핑)
 * @param multiFinalScore   다면 최종 점수 (= 2차)
 * @param multiStatus       다면 평가 진행 상태
 * @param totalScore        종합 점수 (가중 합산, 0~100)
 * @param gradeCode         절대평가 등급 코드 (S/A/B/C/D)
 * @param isConfirmed       최종 확정 여부 (final_grades_51 존재 여부)
 */
@Builder
public record EvaluationResultDTO(
        Long empId,
        String empName,
        String deptName,
        String positionName,
        String jobTitle,
        boolean isLeader,
        // 성과/업무 평가 (MBO) — 일반사원용
        BigDecimal mboSelfScore,
        BigDecimal mbo1stScore,
        BigDecimal mbo2ndScore,
        BigDecimal mboFinalScore,
        String mboStatus,
        // 역량 평가 (COMP) — 일반사원용
        BigDecimal compSelfScore,
        BigDecimal comp1stScore,
        BigDecimal comp2ndScore,
        BigDecimal compFinalScore,
        String compStatus,
        // 다면 평가 (MULTI) — 부서장용
        BigDecimal multiSelfScore,
        BigDecimal multi1stScore,
        BigDecimal multi2ndScore,
        BigDecimal multiFinalScore,
        String multiStatus,
        // 종합
        BigDecimal totalScore,
        String gradeCode,
        boolean isConfirmed
) {
}

package com.ees.eval.service;

public interface FinalGradeProcessor {
    /**
     * 최종 점수를 계산하고, FinalGrade 영속성 처리를 표준화(version=0)하며,
     * 조건에 따라 부서/본부 상대평가 갱신 로직을 실행합니다.
     *
     * @param periodId               평가 기간 ID
     * @param evaluateeId            피평가자 ID
     * @param deptId                 피평가자 부서 ID
     * @param relationTypeCode       평가 매핑의 관계 유형 (EXECUTIVE, MANAGER 등)
     * @param actorEmpId             트랜잭션을 수행하는 사용자 사번
     * @param forceRelativeCalculation 강제로 상대평가를 수행할지 여부 (최종 등급 컨트롤러용)
     */
    void processGradeAndRanking(Long periodId, Long evaluateeId, Long deptId, String relationTypeCode, Long actorEmpId, boolean forceRelativeCalculation);
}

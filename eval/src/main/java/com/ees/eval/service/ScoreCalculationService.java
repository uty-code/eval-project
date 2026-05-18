package com.ees.eval.service;

/**
 * 평가 점수 산출 및 절대평가 등급 매핑을 담당하는 서비스 인터페이스입니다.
 *
 * <p>등급 기준 (절대평가):</p>
 * <ul>
 *     <li>S: 95 ~ 100점 (탁월)</li>
 *     <li>A: 85 ~ 94점 (우수)</li>
 *     <li>B: 75 ~ 84점 (양호)</li>
 *     <li>C: 60 ~ 74점 (보통)</li>
 *     <li>D: 0 ~ 59점 (미흡)</li>
 * </ul>
 */
public interface ScoreCalculationService {

    /**
     * 특정 평가 차수에서 특정 사원의 종합 점수를 계산합니다.
     * 유형별 가중치(evaluation_type_weights_51)를 적용한 가중 합산 방식입니다.
     *
     * @param periodId 평가 차수 ID
     * @param empId    사원 ID
     * @return 종합 점수 (0~100), 평가 데이터가 없으면 null
     */
    Integer calculateTotalScore(Long periodId, Long empId);
    
    /**
     * 특정 평가 차수, 특정 부서의 사원들을 대상으로 상대평가 등급 배분(최대 잔여법)을 수행하고
     * final_grades_51 테이블에 등급을 업데이트합니다.
     *
     * @param periodId 평가 차수 ID
     * @param deptId   부서 ID
     */
    void calculateRelativeGradesForDepartment(Long periodId, Long deptId);

    /**
     * 특정 평가 차수, 특정 본부(상위 부서) 산하의 하위 팀장(부서장)들을 대상으로 상대평가 등급 배분을 수행하고
     * final_grades_51 테이블에 등급을 업데이트합니다.
     *
     * @param periodId     평가 차수 ID
     * @param parentDeptId 상위 부서(본부) ID
     */
    void calculateRelativeGradesForLeadersInHQ(Long periodId, Long parentDeptId);
}

package com.ees.eval.service;

import java.util.Map;

public interface EvaluationSubmitFacadeService {
    /**
     * 평가 내역을 저장하고 최종 등급/상대평가 연산을 단일 트랜잭션으로 처리합니다.
     *
     * @param mappingId          제출할 평가 매핑 ID
     * @param params             평가 제출 폼 파라미터 맵 (점수 등)
     * @param empId              제출자 사번
     * @param periodId           평가 기간 ID
     * @param evaluateeId        피평가자 ID
     * @param deptId             피평가자의 부서 ID
     * @param relationTypeCode   평가 관계 유형
     * @param forceRelativeCalculation 강제 상대평가 실행 여부
     */
    void submitAndProcess(Long mappingId, Map<String, String> params, Long empId, 
                          Long periodId, Long evaluateeId, Long deptId, 
                          String relationTypeCode, boolean forceRelativeCalculation);
}

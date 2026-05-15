package com.ees.eval.service;

import com.ees.eval.dto.EvaluationResultDTO;

import java.util.List;

/**
 * 평가 결과 현황 조회 서비스 인터페이스입니다.
 * 확정된 평가 결과를 유형별 1차/2차/최종 점수와 상태로 조립합니다.
 */
public interface EvaluationResultService {

    /**
     * 특정 차수의 확정된 평가 결과 목록을 조회합니다.
     * 유형별(MBO/COMP/MULTI) 1차/2차/최종 점수와 상태를 포함합니다.
     *
     * @param periodId 평가 차수 ID
     * @param deptId   부서 필터 (null이면 전체)
     * @param search   성명/사번 검색어 (선택)
     * @return 평가 결과 DTO 목록
     */
    List<EvaluationResultDTO> getResults(Long periodId, Long deptId, String search);
}

package com.ees.eval.service;

import com.ees.eval.dto.EvaluationTypeWeightDTO;
import java.util.List;

public interface EvaluationTypeWeightService {
    List<EvaluationTypeWeightDTO> getTypeWeights(Long periodId, Long deptId, String targetRoleCode);
    void saveTypeWeights(Long periodId, Long deptId, String targetRoleCode, List<EvaluationTypeWeightDTO> weights);

    /**
     * 특정 차수/부서의 유형별 가중치 합이 100인지 검증합니다.
     * 부서 전용 설정이 없으면 전사 공통 설정으로 폴백합니다.
     *
     * @param periodId       차수 식별자
     * @param deptId         부서 식별자
     * @param targetRoleCode 대상 역할 코드 (STAFF, LEADER)
     * @return 가중치 합이 정확히 100이면 true
     */
    boolean isWeightSumValid(Long periodId, Long deptId, String targetRoleCode);
}

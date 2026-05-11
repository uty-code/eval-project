package com.ees.eval.service;

import java.util.Map;
import java.util.Set;

/**
 * 평가 데이터(Evaluation) 제출 관련 공통 비즈니스 로직을 담당합니다.
 * 컨트롤러별로 중복되던 Upsert(elementId 파싱 + insert/update) 로직을 통합합니다.
 */
public interface EvaluationService {

    /**
     * 폼 파라미터에서 평가 데이터를 추출하여 Upsert 처리합니다.
     *
     * @param mappingId 평가자 매핑 ID
     * @param params    폼 파라미터 (comment_{id}, score_{id} 형식)
     * @param empId     현재 로그인 사원 ID (신규 생성 시 createdBy/updatedBy)
     * @return 처리된 elementId Set
     * @throws NumberFormatException 점수 파싱 실패 시
     */
    Set<Long> upsertEvaluations(Long mappingId, Map<String, String> params, Long empId);
}

package com.ees.eval.service;

import com.ees.eval.dto.EvaluationElementDTO;

import java.util.List;

/**
 * 평가 항목(EvaluationElement) 관리를 담당하는 서비스 인터페이스입니다.
 * 항목 CRUD와 가중치(Weight) 합계 100 검증 로직을 포함합니다.
 */
public interface EvaluationElementService {

    /**
     * 항목 ID로 평가 항목 상세 정보를 조회합니다.
     *
     * @param elementId 조회할 항목 식별자
     * @return 항목 DTO
     * @throws IllegalArgumentException 해당 항목이 존재하지 않을 경우
     */
    EvaluationElementDTO getElementById(Long elementId);

    /**
     * 특정 차수에 귀속된 평가 항목 목록을 조회합니다.
     *
     * @param periodId 대상 차수 식별자
     * @return 항목 DTO 리스트
     */
    List<EvaluationElementDTO> getElementsByPeriodId(Long periodId);

    /**
     * 신규 평가 항목을 생성합니다.
     * 생성 후 해당 차수의 가중치 합이 100을 초과하는지 검증합니다.
     *
     * @param elementDto 생성할 항목 정보
     * @return 생성된 항목 DTO
     * @throws IllegalStateException 가중치 합이 100을 초과할 경우
     */
    EvaluationElementDTO createElement(EvaluationElementDTO elementDto);

    /**
     * 항목 정보를 수정합니다. 수정 후 가중치 합 검증이 수행됩니다.
     *
     * @param elementDto 수정할 데이터
     * @return 수정 완료된 항목 DTO
     * @throws com.ees.eval.exception.EesOptimisticLockException 데이터 충돌 시
     * @throws IllegalStateException 가중치 합이 100을 초과할 경우
     */
    EvaluationElementDTO updateElement(EvaluationElementDTO elementDto);

    /**
     * 항목을 논리적으로 삭제합니다.
     *
     * @param elementId 삭제할 항목 식별자
     */
    void deleteElement(Long elementId);

    /**
     * 특정 차수의 가중치 합이 정확히 100인지 검증합니다.
     *
     * @param periodId 대상 차수 식별자
     * @return 가중치 합이 100이면 true
     */
    boolean validateWeightSum(Long periodId);
}

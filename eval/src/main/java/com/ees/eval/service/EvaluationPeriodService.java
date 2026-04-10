package com.ees.eval.service;

import com.ees.eval.dto.EvaluationPeriodDTO;

import java.util.List;

/**
 * 평가 차수(EvaluationPeriod) 관리를 담당하는 서비스 인터페이스입니다.
 * 차수 생성, 상태 전이, 진행 중 중복 체크 기능을 제공합니다.
 */
public interface EvaluationPeriodService {

    /**
     * 차수 ID로 평가 차수 상세 정보를 조회합니다.
     *
     * @param periodId 조회할 차수 식별자
     * @return 차수 DTO
     * @throws IllegalArgumentException 해당 차수가 존재하지 않을 경우
     */
    EvaluationPeriodDTO getPeriodById(Long periodId);

    /**
     * 전체 평가 차수 목록을 조회합니다.
     *
     * @return 차수 DTO 리스트
     */
    List<EvaluationPeriodDTO> getAllPeriods();

    /**
     * 신규 평가 차수를 생성합니다. 초기 상태는 PLANNED입니다.
     *
     * @param periodDto 생성할 차수 정보
     * @return 생성된 차수 DTO
     */
    EvaluationPeriodDTO createPeriod(EvaluationPeriodDTO periodDto);

    /**
     * 차수 정보를 수정합니다.
     *
     * @param periodDto 수정할 데이터
     * @return 수정 완료된 차수 DTO
     * @throws com.ees.eval.exception.EesOptimisticLockException 데이터 충돌 시
     */
    EvaluationPeriodDTO updatePeriod(EvaluationPeriodDTO periodDto);

    /**
     * 차수의 상태를 전이합니다. Java 21 Pattern Matching으로 전이 규칙을 검증합니다.
     * 전이 규칙: PLANNED → IN_PROGRESS → COMPLETED → CLOSED
     * IN_PROGRESS는 시스템 전체에서 하나만 허용됩니다.
     *
     * @param periodId 대상 차수 ID
     * @param newStatusCode 전이할 새 상태 코드
     * @return 상태 전이 완료된 차수 DTO
     * @throws IllegalStateException 유효하지 않은 상태 전이 시
     */
    EvaluationPeriodDTO transitionStatus(Long periodId, String newStatusCode);

    /**
     * 차수를 논리적으로 삭제합니다.
     *
     * @param periodId 삭제할 차수 식별자
     */
    void deletePeriod(Long periodId);
}

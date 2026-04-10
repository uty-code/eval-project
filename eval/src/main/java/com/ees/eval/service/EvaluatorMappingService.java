package com.ees.eval.service;

import com.ees.eval.dto.EvaluatorMappingDTO;

import java.util.List;

/**
 * 평가자 매핑(EvaluatorMapping) 관리를 담당하는 서비스 인터페이스입니다.
 * 단건/일괄 매핑 생성, 중복 체크, 자기평가 검증, 평가 목록 조회 기능을 제공합니다.
 */
public interface EvaluatorMappingService {

    /**
     * 매핑 ID로 상세 정보를 조회합니다.
     *
     * @param mappingId 매핑 식별자
     * @return 매핑 DTO (평가자/피평가자 이름 포함)
     * @throws IllegalArgumentException 존재하지 않을 경우
     */
    EvaluatorMappingDTO getMappingById(Long mappingId);

    /**
     * 특정 차수의 전체 매핑 목록을 조회합니다.
     *
     * @param periodId 차수 식별자
     * @return 매핑 DTO 리스트
     */
    List<EvaluatorMappingDTO> getMappingsByPeriodId(Long periodId);

    /**
     * 특정 차수에서 '내가 수행해야 할 평가 목록'을 조회합니다.
     *
     * @param periodId 차수 ID
     * @param evaluatorId 평가자(나) 사원 ID
     * @return 내가 평가해야 할 매핑 리스트
     */
    List<EvaluatorMappingDTO> getMyEvaluationTasks(Long periodId, Long evaluatorId);

    /**
     * 특정 차수에서 '나를 평가하는 사람 목록'을 조회합니다.
     *
     * @param periodId 차수 ID
     * @param evaluateeId 피평가자(나) 사원 ID
     * @return 나를 평가하는 매핑 리스트
     */
    List<EvaluatorMappingDTO> getMyEvaluators(Long periodId, Long evaluateeId);

    /**
     * 단건 평가자 매핑을 생성합니다.
     * 자기 자신을 SUPERIOR/PEER로 매핑하는 것과 중복 매핑을 차단합니다.
     *
     * @param mappingDto 생성할 매핑 정보
     * @return 생성된 매핑 DTO
     * @throws IllegalArgumentException 자기 자신을 SUPERIOR/PEER로 매핑할 경우
     * @throws IllegalStateException 동일 관계가 이미 존재할 경우
     */
    EvaluatorMappingDTO createMapping(EvaluatorMappingDTO mappingDto);

    /**
     * 한 명의 피평가자에게 여러 명의 평가자를 한 번에 일괄 매핑합니다.
     * 가상 스레드 환경에서 효율적으로 처리되도록 @Transactional이 적용됩니다.
     *
     * @param periodId 차수 ID
     * @param evaluateeId 피평가자 사원 ID
     * @param evaluatorIds 매핑할 평가자 ID 목록
     * @param relationTypeCode 관계 유형 코드
     * @return 생성된 매핑 DTO 리스트
     */
    List<EvaluatorMappingDTO> createBulkMappings(Long periodId, Long evaluateeId,
                                                  List<Long> evaluatorIds, String relationTypeCode);

    /**
     * 매핑을 논리적으로 삭제합니다.
     *
     * @param mappingId 삭제할 매핑 ID
     */
    void deleteMapping(Long mappingId);
}

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
     * 특정 차수의 매핑 목록을 부서별, 이름별로 필터링하여 조회합니다.
     *
     * @param periodId     차수 식별자
     * @param deptId       부서 식별자 (null이면 전체 조회)
     * @param searchName   사원명 검색어 (null이면 전체 조회)
     * @return 매핑 DTO 리스트
     */
    List<EvaluatorMappingDTO> getMappingsByPeriodIdAndDeptId(Long periodId, Long deptId, String searchName);

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
     * 특정 차수의 평가자 매핑을 자동 생성합니다. (본인 및 부서장 매핑)
     *
     * @param periodId     차수 식별자
     * @param deptId       부서 식별자 (null이면 전체 사원 대상)
     * @param excludeEmpId 제외할 사원 식별자 (null이면 제외 없음)
     * @return 생성된 매핑 수
     */
    int autoGenerateMappings(Long periodId, Long deptId, Long excludeEmpId);

    /**
     * 매핑을 논리적으로 삭제합니다.
     *
     * @param mappingId 삭제할 매핑 ID
     */
    void deleteMapping(Long mappingId);

    /**
     * 기존 매핑의 평가자를 변경합니다.
     *
     * @param mappingId   매핑 ID
     * @param evaluatorId 새 평가자 ID
     * @return 업데이트된 매핑 DTO
     */
    EvaluatorMappingDTO updateMapping(Long mappingId, Long evaluatorId);

    /**
     * 특정 차수 및 부서의 모든 매핑을 일괄 삭제(초기화)합니다.
     *
     * @param periodId 차수 ID
     * @param deptId   부서 ID (null일 경우 전체 삭제)
     */
    void initializeMappingsByDept(Long periodId, Long deptId);
}

package com.ees.eval.service;

import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.dto.MappingAnomalyDTO;

import java.util.List;

/**
 * 평가자 매핑(EvaluatorMapping) 관리를 담당하는 서비스 인터페이스입니다.
 * 단건/일괄 매핑 생성, 중복 체크, 자기평가 검증, 평가 목록 조회 기능을 제공합니다.
 *
 * <p><strong>핵심 비즈니스 규칙:</strong></p>
 * <ul>
 *   <li>모든 매핑 변경(CUD) 작업은 해당 차수(period_id)의 상태가 {@code PLANNED}일 때만 가능합니다.</li>
 *   <li>{@code IN_PROGRESS} 이상 상태에서 매핑 수정 시도 시 {@link IllegalStateException}이 발생합니다.</li>
 *   <li>논리적 삭제: 데이터 무결성을 위해 삭제 및 수정 시 기존 데이터는 {@code is_deleted='y'} 처리됩니다.</li>
 *   <li>자기 자신을 관리자나 동료로 매핑할 수 없습니다. (자기 평가는 SELF_EVAL 관계로 별도 관리)</li>
 * </ul>
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
     * 자기 자신을 MANAGER/SUBORDINATE 등으로 매핑하는 것과 중복 매핑을 차단합니다.
     * 
     * @param mappingDto 생성할 매핑 정보 (periodId 필수)
     * @return 생성된 매핑 DTO
     * @throws IllegalArgumentException 자기 자신을 MANAGER/SUBORDINATE 등으로 매핑할 경우
     * @throws IllegalStateException 동일 관계가 이미 존재하거나, 평가 차수가 PLANNED 상태가 아닐 경우
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
     * @throws IllegalStateException 평가 차수가 PLANNED 상태가 아닐 경우
     */
    List<EvaluatorMappingDTO> createBulkMappings(Long periodId, Long evaluateeId,
                                                  List<Long> evaluatorIds, String relationTypeCode);

    /**
     * 특정 차수의 평가자 매핑을 자동 생성합니다. (본인 및 부서장 매핑)
     * 기존 매핑이 있을 경우, 권한 충돌 방지를 위해 논리적 삭제(is_deleted='y') 후 새로 생성합니다.
     *
     * @param periodId     차수 식별자
     * @param deptId       부서 식별자 (null이면 전체 사원 대상)
     * @param excludeEmpId 제외할 사원 식별자 (null이면 제외 없음)
     * @return 생성된 매핑 수
     * @throws IllegalStateException 평가 차수가 PLANNED 상태가 아닐 경우
     */
    int autoGenerateMappings(Long periodId, Long deptId, Long excludeEmpId);

    /**
     * 매핑을 논리적으로 삭제합니다. (실제 데이터는 보존되며 is_deleted 플래그만 'y'로 변경)
     *
     * @param mappingId 삭제할 매핑 ID
     * @throws IllegalStateException 매핑이 속한 평가 차수가 PLANNED 상태가 아닐 경우
     */
    void deleteMapping(Long mappingId);

    /**
     * 기존 매핑의 평가자를 변경합니다.
     * 데이터 무결성 및 이력 관리를 위해 기존 매핑은 논리 삭제 처리하고 새로운 매핑을 생성합니다.
     *
     * @param mappingId   매핑 ID
     * @param evaluatorId 새 평가자 ID
     * @return 업데이트(새로 생성)된 매핑 DTO
     * @throws IllegalStateException 평가 차수가 PLANNED 상태가 아닐 경우
     */
    EvaluatorMappingDTO updateMapping(Long mappingId, Long evaluatorId);

    /**
     * 특정 차수 및 부서의 모든 매핑을 일괄 삭제(초기화)합니다.
     * 테스트 및 시스템 리셋 목적으로 주로 활용됩니다.
     *
     * @param periodId 차수 ID
     * @param deptId   부서 ID (null일 경우 전체 삭제)
     * @throws IllegalStateException 평가 차수가 PLANNED 상태가 아닐 경우
     */
    void initializeMappingsByDept(Long periodId, Long deptId);

    /**
     * 특정 차수의 평가 매핑 정합성을 검증하여 누락 및 오류를 반환합니다.
     *
     * @param periodId 검증할 평가 차수 ID
     * @return 예외 상황(MappingAnomalyDTO) 목록
     */
    List<MappingAnomalyDTO> checkMappingIntegrity(Long periodId);

    /**
     * 현재 평가 매핑이 하위 단계 평가 제출로 인해 잠겨 있는지 확인합니다.
     * 
     * @param mappingId 확인할 매핑 ID
     * @return 잠금 여부 및 잠금 사유 정보를 담은 맵 (isLocked: boolean, lockedBy: String)
     */
    java.util.Map<String, Object> checkEvaluationLock(Long mappingId);

    /**
     * 다수의 매핑에 대한 잠금 여부를 사전 조회된 데이터를 활용하여 일괄 확인합니다.
     * 컨트롤러에서 이미 벌크 조회한 매핑/평가 데이터를 재사용하여 DB 호출을 최소화합니다.
     *
     * @param mappingIds         잠금 확인할 매핑 ID 목록
     * @param allMappingsByEvaluatee 피평가자별 전체 매핑 맵 (사전 조회 데이터)
     * @param evalGroupMap       매핑 ID별 평가 데이터 맵 (사전 조회 데이터)
     * @return mappingId → 잠금 여부 맵
     */
    java.util.Map<Long, Boolean> checkEvaluationLockBulk(
            java.util.List<Long> mappingIds,
            java.util.Map<Long, java.util.List<com.ees.eval.domain.EvaluatorMapping>> allMappingsByEvaluatee,
            java.util.Map<Long, java.util.List<com.ees.eval.domain.Evaluation>> evalGroupMap);

    /**
     * 나의 자가평가 목록을 필터링 및 페이징하여 조회합니다.
     * 
     * @param evaluatorId  평가자(나) 사원 ID
     * @param periodId     평가 차수 ID (null이면 전체)
     * @param filterStatus 상태 필터 (null이면 전체)
     * @param keyword      검색어 (null이면 전체)
     * @param page         페이지 번호 (1부터 시작)
     * @param pageSize     페이지 크기
     * @return 자가평가 페이징 데이터 (MyEvaluationPageDTO)
     */
    com.ees.eval.dto.MyEvaluationPageDTO getMyEvaluationDashboardTasks(
            Long evaluatorId, Long periodId, String filterStatus, String keyword, int page, int pageSize);

    /**
     * 다면평가 대상 목록을 필터링 및 페이징하여 조회합니다.
     * 
     * @param periodId     평가 차수 ID
     * @param evaluatorId  평가자(나) 사원 ID
     * @param filterDeptId 부서 필터 (null이면 전체)
     * @param filterStatus 상태 필터 (null이면 전체)
     * @param keyword      검색어 (null이면 전체)
     * @param page         페이지 번호 (1부터 시작)
     * @param pageSize     페이지 크기
     * @return 다면평가 페이징 데이터 (MultiDimensionalEvalPageDTO)
     */
    com.ees.eval.dto.MultiDimensionalEvalPageDTO getMultiDimensionalTasks(
            Long periodId, Long evaluatorId, Long filterDeptId, String filterStatus, String keyword, int page, int pageSize, boolean isPeriodActive);

    /**
     * 어드민용: 전체 자가평가 대시보드 태스크를 조회합니다.
     * 특정 사원(evaluator_id)에 제한하지 않고 모든 SELF 매핑을 조회합니다.
     *
     * @param periodId     평가 차수 ID (null이면 전체)
     * @param filterStatus 상태 필터 (null이면 전체)
     * @param keyword      검색어 (null이면 전체)
     * @param page         페이지 번호 (1부터 시작)
     * @param pageSize     페이지 크기
     * @return 자가평가 페이징 데이터 (MyEvaluationPageDTO)
     */
    com.ees.eval.dto.MyEvaluationPageDTO getAdminMyEvaluationDashboardTasks(
            Long periodId, String filterStatus, String keyword, int page, int pageSize);

    /**
     * 어드민용: 전체 다면평가 태스크를 조회합니다.
     * 특정 평가자에 제한하지 않고 모든 SUBORDINATE 매핑을 조회합니다.
     *
     * @param periodId      평가 차수 ID (null이면 전체)
     * @param filterDeptId  부서 필터 (null이면 전체)
     * @param filterStatus  상태 필터 (null이면 전체)
     * @param keyword       검색어 (null이면 전체)
     * @param page          페이지 번호
     * @param pageSize      페이지 크기
     * @param isPeriodActive 차수 활성 여부
     * @return 다면평가 페이징 데이터
     */
    com.ees.eval.dto.MultiDimensionalEvalPageDTO getAdminMultiDimensionalTasks(
            Long periodId, Long filterDeptId, String filterStatus, String keyword, int page, int pageSize, boolean isPeriodActive);

    /**
     * 어드민용: 전체 성과/역량 평가 태스크를 조회합니다.
     * 모든 MANAGER/EXECUTIVE 매핑을 evaluator_id 필터 없이 조회합니다.
     *
     * @param periodId 평가 차수 ID (null이면 전체)
     * @return 전체 성과/역량 매핑 DTO 리스트
     */
    List<EvaluatorMappingDTO> getAllPerformanceTasks(Long periodId);
}

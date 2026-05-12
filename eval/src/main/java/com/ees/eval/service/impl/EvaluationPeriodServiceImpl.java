package com.ees.eval.service.impl;

import com.ees.eval.domain.EvaluationPeriod;
import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.dto.MappingAnomalyDTO;
import com.ees.eval.mapper.EvaluationPeriodMapper;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.EvaluatorMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EvaluationPeriodService의 실제 비즈니스 로직 구현체입니다.
 * 차수 상태 전이 시 Java 21 Pattern Matching for switch를 활용하며,
 * '진행 중(IN_PROGRESS)' 상태의 중복을 방지합니다.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class EvaluationPeriodServiceImpl implements EvaluationPeriodService {

    private final EvaluationPeriodMapper periodMapper;
    private final EvaluationTypeWeightService typeWeightService;
    private final DepartmentService departmentService;
    private final EvaluatorMappingService mappingService;
    private final com.ees.eval.mapper.EvaluationMapper evaluationMapper;
    private final com.ees.eval.mapper.FinalGradeMapper finalGradeMapper;

    /** 상태 코드 상수 정의 */
    private static final String STATUS_PLANNED = "PLANNED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CLOSED = "CLOSED";

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public EvaluationPeriodDTO getPeriodById(Long periodId) {
        // 매퍼를 통해 차수 엔티티 조회
        EvaluationPeriod period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("평가 차수를 찾을 수 없습니다. periodId: " + periodId));
        return convertToDto(period);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "eval-periods", key = "'all'")
    public List<EvaluationPeriodDTO> getAllPeriods() {
        return periodMapper.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "eval-periods", key = "'in-progress'")
    public List<EvaluationPeriodDTO> getInProgressPeriods() {
        return periodMapper.findByStatusCode(STATUS_IN_PROGRESS).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @CacheEvict(value = "eval-periods", allEntries = true)
    public EvaluationPeriodDTO createPeriod(EvaluationPeriodDTO periodDto) {
        // 엔티티 변환 후 초기 상태를 PLANNED로 강제 설정
        EvaluationPeriod period = convertToEntity(periodDto);
        period.setStatusCode(STATUS_PLANNED);
        period.prePersist();

        periodMapper.insert(period);
        return getPeriodById(period.getPeriodId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @CacheEvict(value = "eval-periods", allEntries = true)
    public EvaluationPeriodDTO updatePeriod(EvaluationPeriodDTO periodDto) {
        EvaluationPeriod period = convertToEntity(periodDto);
        period.preUpdate();

        int updatedRows = periodMapper.update(period);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("차수 정보가 다른 사용자에 의해 변경되었거나 수정 충돌이 발생했습니다.");
        }
        return getPeriodById(period.getPeriodId());
    }

    /**
     * {@inheritDoc}
     * Java 21 Pattern Matching for switch를 활용하여 상태 전이 규칙을 안전하게 검증합니다.
     */
    @Override
    @Transactional
    @CacheEvict(value = "eval-periods", allEntries = true)
    public EvaluationPeriodDTO transitionStatus(Long periodId, String newStatusCode) {
        // 1. 현재 차수 조회
        EvaluationPeriod period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("평가 차수를 찾을 수 없습니다. periodId: " + periodId));

        String currentStatus = period.getStatusCode();

        // 2. Java 21 Pattern Matching for switch: 상태 전이 규칙 검증
        String validatedNewStatus = switch (currentStatus) {
            case String s when s.equals(STATUS_PLANNED) && newStatusCode.equals(STATUS_IN_PROGRESS) -> {
                log.info("차수 상태 전이 시작 [PLANNED -> IN_PROGRESS] - periodId: {}", periodId);
                // PLANNED → IN_PROGRESS: 진행 중 중복 체크 수행
                List<EvaluationPeriod> inProgressList = periodMapper.findByStatusCode(STATUS_IN_PROGRESS);
                if (!inProgressList.isEmpty()) {
                    throw new IllegalStateException(
                            "현재 '진행 중' 상태인 차수가 이미 존재합니다. [" +
                                    inProgressList.getFirst().getPeriodName() + "] " +
                                    "기존 차수를 완료 처리한 후 다시 시도해 주세요.");
                }

                // 가중치 설정 완료 여부 검증 (전사 공통 + 모든 부서)
                log.info("가중치 설정 검증 시작 - periodId: {}", periodId);
                validateAllWeightsConfigured(periodId);
                log.info("가중치 설정 검증 완료 - periodId: {}", periodId);

                // 평가자 매핑 정합성 검증 (ERROR 등급만 차단)
                log.info("평가자 매핑 정합성 검증 시작 - periodId: {}", periodId);
                validateEvaluatorMappings(periodId);
                log.info("평가자 매핑 정합성 검증 완료 - periodId: {}", periodId);

                yield STATUS_IN_PROGRESS;
            }
            case String s when s.equals(STATUS_IN_PROGRESS) && newStatusCode.equals(STATUS_COMPLETED) -> {
                log.info("차수 상태 전이 [IN_PROGRESS -> COMPLETED] - periodId: {}", periodId);
                yield STATUS_COMPLETED;
            }
            case String s when s.equals(STATUS_COMPLETED) && newStatusCode.equals(STATUS_CLOSED) -> {
                log.info("차수 상태 전이 [COMPLETED -> CLOSED] - periodId: {}", periodId);
                yield STATUS_CLOSED;
            }
            default ->
                throw new IllegalStateException(
                        "유효하지 않은 상태 전이입니다: [" + currentStatus + "] → [" + newStatusCode + "]. " +
                                "허용 경로: PLANNED → IN_PROGRESS → COMPLETED → CLOSED");
        };

        // 3. 상태 업데이트 수행
        period.setStatusCode(validatedNewStatus);
        period.preUpdate();

        log.info("상태 업데이트 DB 반영 시작 - periodId: {}, status: {}", periodId, validatedNewStatus);
        int updatedRows = periodMapper.update(period);
        if (updatedRows == 0) {
            log.error("상태 업데이트 실패 (Optimistic Lock) - periodId: {}", periodId);
            throw new EesOptimisticLockException("차수 상태 전이 중 충돌이 발생했습니다.");
        }
        log.info("상태 업데이트 DB 반영 완료 - periodId: {}", periodId);
        return getPeriodById(periodId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @CacheEvict(value = "eval-periods", allEntries = true)
    public void deletePeriod(Long periodId) {
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        int updatedRows = periodMapper.softDelete(periodId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 차수를 찾을 수 없습니다. periodId: " + periodId);
        }
    }

    /**
     * 평가 시작 전 모든 가중치 설정이 올바른지 검증합니다.
     * 전사 공통(deptId=null)과 모든 부서에 대해 STAFF 역할의 가중치를 검증합니다.
     *
     * @param periodId 검증 대상 차수 식별자
     * @throws IllegalStateException 가중치 설정이 미완료된 부서가 존재할 경우
     */
    private void validateAllWeightsConfigured(Long periodId) {
        List<String> invalidScopes = new ArrayList<>();

        // 모든 부서별 가중치 검증 (부서 자체 설정이 100%여야 함)
        List<DepartmentDTO> allDepts = departmentService.getSimpleAllDepartments();
        for (DepartmentDTO dept : allDepts) {
            // 비활성 부서는 검증에서 제외
            if ("n".equalsIgnoreCase(dept.isActive())) {
                continue;
            }

            boolean isStaffValid = typeWeightService.isWeightSumValid(periodId, dept.deptId(), "STAFF");
            boolean isLeaderValid = typeWeightService.isWeightSumValid(periodId, dept.deptId(), "LEADER");
            
            if (!isStaffValid || !isLeaderValid) {
                // 부서의 가중치가 100%가 아니면 (미설정 포함) 에러 목록에 추가
                invalidScopes.add(dept.deptName());
            }
        }

        if (!invalidScopes.isEmpty()) {
            throw new IllegalStateException(
                    "가중치 설정이 완료되지 않은 대상이 있습니다: [" +
                            String.join(", ", invalidScopes) + "]. " +
                            "해당 부서의 평가요소 관리에서 유형별 가중치 및 항목별 가중치 합계가 100%가 되도록 설정해 주세요.");
        }
    }

    /**
     * 평가 시작 전 평가자 매핑 정합성을 검증합니다.
     * 기존 EvaluatorMappingService의 checkMappingIntegrity를 활용하여
     * ERROR 등급의 매핑 이상이 발견되면 평가 시작을 차단합니다.
     *
     * @param periodId 검증 대상 차수 식별자
     * @throws IllegalStateException ERROR 등급 매핑 이상이 존재할 경우
     */
    private void validateEvaluatorMappings(Long periodId) {
        List<MappingAnomalyDTO> anomalies = mappingService.checkMappingIntegrity(periodId);

        // ERROR 등급만 필터링
        List<MappingAnomalyDTO> errors = anomalies.stream()
                .filter(a -> "ERROR".equals(a.severity()))
                .toList();

        if (!errors.isEmpty()) {
            // 첫 번째 에러의 상세 정보를 예시로 포함
            MappingAnomalyDTO firstError = errors.getFirst();
            throw new IllegalStateException(
                    "평가자 매핑 오류가 " + errors.size() + "건 발견되었습니다. " +
                    "(예: " + firstError.evaluateeName() + " - " + firstError.description() + ") " +
                    "평가자 매핑 관리에서 정합성 검사를 확인한 후 다시 시도해 주세요.");
        }
    }

    /**
     * 엔티티를 DTO 레코드로 변환합니다.
     */
    private EvaluationPeriodDTO convertToDto(EvaluationPeriod period) {
        return EvaluationPeriodDTO.builder()
                .periodId(period.getPeriodId())
                .periodYear(period.getPeriodYear())
                .periodName(period.getPeriodName())
                .statusCode(period.getStatusCode())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .isDeleted(period.getIsDeleted())
                .version(period.getVersion())
                .createdAt(period.getCreatedAt())
                .createdBy(period.getCreatedBy())
                .updatedAt(period.getUpdatedAt())
                .updatedBy(period.getUpdatedBy())
                .build();
    }

    /**
     * DTO 레코드를 엔티티로 변환합니다.
     */
    private EvaluationPeriod convertToEntity(EvaluationPeriodDTO dto) {
        EvaluationPeriod period = EvaluationPeriod.builder()
                .periodId(dto.periodId())
                .periodYear(dto.periodYear())
                .periodName(dto.periodName())
                .statusCode(dto.statusCode())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .build();
        period.setIsDeleted(dto.isDeleted());
        period.setVersion(dto.version());
        period.setCreatedAt(dto.createdAt());
        period.setCreatedBy(dto.createdBy());
        period.setUpdatedAt(dto.updatedAt());
        period.setUpdatedBy(dto.updatedBy());
        return period;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public long countByStatusCode(String statusCode) {
        return periodMapper.countByStatusCode(statusCode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return periodMapper.countAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @CacheEvict(value = "eval-periods", allEntries = true)
    public void resetPeriod(Long periodId) {
        // 1. 차수 조회
        EvaluationPeriod period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("평가 차수를 찾을 수 없습니다. periodId: " + periodId));

        // 2. 상태 확인 (진행 중인 경우만 초기화 가능하도록 제한할 수 있으나, 일단 모든 상태에서 가능하게 함)
        // 단, 이미 CLOSED된 경우 등은 막는 것이 안전할 수 있음.

        // 3. 평가 데이터 삭제 (상세 점수 + 최종 등급)
        evaluationMapper.deleteByPeriodId(periodId);
        finalGradeMapper.deleteByPeriodId(periodId);

        // 4. 상태를 PLANNED로 변경
        period.setStatusCode(STATUS_PLANNED);
        period.preUpdate();
        periodMapper.update(period);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EvaluationPeriodDTO resolveSelectedPeriod(Long periodId, List<EvaluationPeriodDTO> periods) {
        if (periodId != null) {
            return getPeriodById(periodId);
        }
        if (periods == null || periods.isEmpty()) {
            return null;
        }
        return periods.stream()
                .filter(p -> STATUS_IN_PROGRESS.equals(p.statusCode()))
                .findFirst()
                .orElse(periods.get(0));
    }
}

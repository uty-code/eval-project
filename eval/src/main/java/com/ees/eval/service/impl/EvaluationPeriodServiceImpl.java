package com.ees.eval.service.impl;

import com.ees.eval.domain.EvaluationPeriod;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.EvaluationPeriodMapper;
import com.ees.eval.service.EvaluationPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EvaluationPeriodService의 실제 비즈니스 로직 구현체입니다.
 * 차수 상태 전이 시 Java 21 Pattern Matching for switch를 활용하며,
 * '진행 중(IN_PROGRESS)' 상태의 중복을 방지합니다.
 */
@Service
@RequiredArgsConstructor
public class EvaluationPeriodServiceImpl implements EvaluationPeriodService {

    private final EvaluationPeriodMapper periodMapper;

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
    public List<EvaluationPeriodDTO> getAllPeriods() {
        return periodMapper.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
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
    public EvaluationPeriodDTO transitionStatus(Long periodId, String newStatusCode) {
        // 1. 현재 차수 조회
        EvaluationPeriod period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("평가 차수를 찾을 수 없습니다. periodId: " + periodId));

        String currentStatus = period.getStatusCode();

        // 2. Java 21 Pattern Matching for switch: 상태 전이 규칙 검증
        String validatedNewStatus = switch (currentStatus) {
            case String s when s.equals(STATUS_PLANNED) && newStatusCode.equals(STATUS_IN_PROGRESS) -> {
                // PLANNED → IN_PROGRESS: 진행 중 중복 체크 수행
                List<EvaluationPeriod> inProgressList = periodMapper.findByStatusCode(STATUS_IN_PROGRESS);
                if (!inProgressList.isEmpty()) {
                    throw new IllegalStateException(
                            "현재 '진행 중' 상태인 차수가 이미 존재합니다. [" +
                                    inProgressList.getFirst().getPeriodName() + "] " +
                                    "기존 차수를 완료 처리한 후 다시 시도해 주세요.");
                }
                yield STATUS_IN_PROGRESS;
            }
            case String s when s.equals(STATUS_IN_PROGRESS) && newStatusCode.equals(STATUS_COMPLETED) ->
                STATUS_COMPLETED;
            case String s when s.equals(STATUS_COMPLETED) && newStatusCode.equals(STATUS_CLOSED) ->
                STATUS_CLOSED;
            default ->
                throw new IllegalStateException(
                        "유효하지 않은 상태 전이입니다: [" + currentStatus + "] → [" + newStatusCode + "]. " +
                                "허용 경로: PLANNED → IN_PROGRESS → COMPLETED → CLOSED");
        };

        // 3. 상태 업데이트 수행
        period.setStatusCode(validatedNewStatus);
        period.preUpdate();

        int updatedRows = periodMapper.update(period);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("차수 상태 전이 중 충돌이 발생했습니다.");
        }
        return getPeriodById(periodId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deletePeriod(Long periodId) {
        Long currentUserId = 1L;
        int updatedRows = periodMapper.softDelete(periodId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 차수를 찾을 수 없습니다. periodId: " + periodId);
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
}

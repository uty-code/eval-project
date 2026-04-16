package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluatorMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EvaluatorMappingService의 실제 비즈니스 로직 구현체입니다.
 * 자기평가 검증, 중복 매핑 차단, 일괄 매핑 처리를 수행합니다.
 * 가상 스레드 환경에서 @Transactional로 원자성을 보장합니다.
 */
@Service
@RequiredArgsConstructor
public class EvaluatorMappingServiceImpl implements EvaluatorMappingService {

    private final EvaluatorMappingMapper mappingMapper;
    private final EmployeeMapper employeeMapper;

    /** 자기 자신을 매핑할 수 없는 관계 유형 목록 */
    private static final String RELATION_SUPERIOR = "SUPERIOR";
    private static final String RELATION_PEER = "PEER";
    private static final String RELATION_SELF = "SELF";
    private static final String RELATION_SUBORDINATE = "SUBORDINATE";

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public EvaluatorMappingDTO getMappingById(Long mappingId) {
        EvaluatorMapping mapping = mappingMapper.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("매핑을 찾을 수 없습니다. mappingId: " + mappingId));
        return enrichDto(mapping);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluatorMappingDTO> getMappingsByPeriodId(Long periodId) {
        return mappingMapper.findByPeriodId(periodId).stream()
                .map(this::enrichDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluatorMappingDTO> getMyEvaluationTasks(Long periodId, Long evaluatorId) {
        // 내가 평가자로 지정된 매핑 → 내가 수행해야 할 평가 리스트
        return mappingMapper.findByEvaluatorId(periodId, evaluatorId).stream()
                .map(this::enrichDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluatorMappingDTO> getMyEvaluators(Long periodId, Long evaluateeId) {
        // 내가 피평가자로 지정된 매핑 → 나를 평가하는 사람 리스트
        return mappingMapper.findByEvaluateeId(periodId, evaluateeId).stream()
                .map(this::enrichDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvaluatorMappingDTO createMapping(EvaluatorMappingDTO mappingDto) {
        // 1. 자기 자신 매핑 검증 (Java 21 Pattern Matching for switch)
        validateSelfMapping(mappingDto.evaluateeId(), mappingDto.evaluatorId(), mappingDto.relationTypeCode());

        // 2. 동일 관계 중복 체크
        validateDuplicate(mappingDto.periodId(), mappingDto.evaluateeId(),
                mappingDto.evaluatorId(), mappingDto.relationTypeCode());

        // 3. 엔티티 변환 및 저장
        EvaluatorMapping mapping = convertToEntity(mappingDto);
        mapping.prePersist();
        mappingMapper.insert(mapping);

        return enrichDto(mapping);
    }

    /**
     * {@inheritDoc}
     * 한 명의 피평가자에게 여러 평가자를 일괄 매핑합니다.
     * 단일 트랜잭션으로 처리하여 가상 스레드 환경에서도 원자성을 보장합니다.
     */
    @Override
    @Transactional
    public List<EvaluatorMappingDTO> createBulkMappings(Long periodId, Long evaluateeId,
            List<Long> evaluatorIds, String relationTypeCode) {
        List<EvaluatorMappingDTO> results = new ArrayList<>();

        for (Long evaluatorId : evaluatorIds) {
            // 각 평가자에 대해 검증 및 저장 수행
            validateSelfMapping(evaluateeId, evaluatorId, relationTypeCode);
            validateDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);

            EvaluatorMapping mapping = EvaluatorMapping.builder()
                    .periodId(periodId)
                    .evaluateeId(evaluateeId)
                    .evaluatorId(evaluatorId)
                    .relationTypeCode(relationTypeCode)
                    .build();
            mapping.prePersist();
            mappingMapper.insert(mapping);

            results.add(enrichDto(mapping));
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMapping(Long mappingId) {
        Long currentUserId = 1L;
        int updatedRows = mappingMapper.softDelete(mappingId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 매핑을 찾을 수 없습니다. mappingId: " + mappingId);
        }
    }

    /**
     * 자기 자신을 SUPERIOR 또는 PEER로 매핑하는 것을 차단하는 검증 로직입니다.
     * Java 21 Pattern Matching for switch를 활용합니다.
     *
     * @param evaluateeId      피평가자 ID
     * @param evaluatorId      평가자 ID
     * @param relationTypeCode 관계 유형
     * @throws IllegalArgumentException 자기 자신을 SUPERIOR/PEER로 매핑할 경우
     */
    private void validateSelfMapping(Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        if (evaluateeId.equals(evaluatorId)) {
            // Java 21: Pattern Matching for switch로 허용/차단 판별
            switch (relationTypeCode) {
                case String r when r.equals(RELATION_SELF) -> {
                    // SELF 관계는 자기 자신 매핑 허용
                }
                case String r when r.equals(RELATION_SUPERIOR) ->
                    throw new IllegalArgumentException("자기 자신을 상급자(SUPERIOR) 평가자로 지정할 수 없습니다.");
                case String r when r.equals(RELATION_PEER) ->
                    throw new IllegalArgumentException("자기 자신을 동료(PEER) 평가자로 지정할 수 없습니다.");
                case String r when r.equals(RELATION_SUBORDINATE) ->
                    throw new IllegalArgumentException("자기 자신을 하급자(SUBORDINATE) 평가자로 지정할 수 없습니다.");
                default ->
                    throw new IllegalArgumentException("알 수 없는 관계 유형입니다: " + relationTypeCode);
            }
        }
    }

    /**
     * 동일 차수에서 동일한 평가 관계의 중복 생성을 방지합니다.
     *
     * @param periodId         차수 ID
     * @param evaluateeId      피평가자 ID
     * @param evaluatorId      평가자 ID
     * @param relationTypeCode 관계 유형
     * @throws IllegalStateException 중복 매핑이 존재할 경우
     */
    private void validateDuplicate(Long periodId, Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        int count = mappingMapper.countDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);
        if (count > 0) {
            throw new IllegalStateException(
                    "동일한 평가 관계가 이미 존재합니다. [차수:" + periodId +
                            ", 피평가자:" + evaluateeId + ", 평가자:" + evaluatorId +
                            ", 관계:" + relationTypeCode + "]");
        }
    }

    /**
     * 매핑 엔티티를 DTO로 변환하며, 사원 이름 정보를 추가 조회하여 채웁니다.
     *
     * @param mapping 원본 매핑 엔티티
     * @return 사원 이름이 포함된 DTO
     */
    private EvaluatorMappingDTO enrichDto(EvaluatorMapping mapping) {
        // 피평가자 이름 조회
        String evaluateeName = employeeMapper.findById(mapping.getEvaluateeId())
                .map(Employee::getName).orElse("알 수 없음");

        // 평가자 이름 조회
        String evaluatorName = employeeMapper.findById(mapping.getEvaluatorId())
                .map(Employee::getName).orElse("알 수 없음");

        return EvaluatorMappingDTO.builder()
                .mappingId(mapping.getMappingId())
                .periodId(mapping.getPeriodId())
                .evaluateeId(mapping.getEvaluateeId())
                .evaluatorId(mapping.getEvaluatorId())
                .relationTypeCode(mapping.getRelationTypeCode())
                .evaluateeName(evaluateeName)
                .evaluatorName(evaluatorName)
                .isDeleted(mapping.getIsDeleted())
                .version(mapping.getVersion())
                .createdAt(mapping.getCreatedAt())
                .createdBy(mapping.getCreatedBy())
                .updatedAt(mapping.getUpdatedAt())
                .updatedBy(mapping.getUpdatedBy())
                .build();
    }

    /**
     * DTO를 엔티티로 변환합니다.
     */
    private EvaluatorMapping convertToEntity(EvaluatorMappingDTO dto) {
        EvaluatorMapping mapping = EvaluatorMapping.builder()
                .mappingId(dto.mappingId())
                .periodId(dto.periodId())
                .evaluateeId(dto.evaluateeId())
                .evaluatorId(dto.evaluatorId())
                .relationTypeCode(dto.relationTypeCode())
                .build();
        mapping.setIsDeleted(dto.isDeleted());
        mapping.setVersion(dto.version());
        mapping.setCreatedAt(dto.createdAt());
        mapping.setCreatedBy(dto.createdBy());
        mapping.setUpdatedAt(dto.updatedAt());
        mapping.setUpdatedBy(dto.updatedBy());
        return mapping;
    }
}

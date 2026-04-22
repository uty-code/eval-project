package com.ees.eval.service.impl;

import com.ees.eval.domain.EvaluationElement;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.EvaluationElementMapper;
import com.ees.eval.service.EvaluationElementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EvaluationElementService의 실제 비즈니스 로직 구현체입니다.
 * 평가 항목의 CRUD와 가중치 합계 100 검증 로직을 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class EvaluationElementServiceImpl implements EvaluationElementService {

    private final EvaluationElementMapper elementMapper;

    /** 가중치 합이 도달해야 할 기준 값 */
    private static final BigDecimal WEIGHT_LIMIT = new BigDecimal("100.00");

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public EvaluationElementDTO getElementById(Long elementId) {
        EvaluationElement element = elementMapper.findById(elementId)
                .orElseThrow(() -> new IllegalArgumentException("평가 항목을 찾을 수 없습니다. elementId: " + elementId));
        return convertToDto(element);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluationElementDTO> getElementsByPeriodId(Long periodId, Long deptId) {
        // 해당 부서(또는 deptId가 null인 경우 전사 공통) 전용 설정만 조회합니다.
        List<EvaluationElement> elements = elementMapper.findByPeriodId(periodId, deptId);

        return elements.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvaluationElementDTO createElement(EvaluationElementDTO elementDto) {
        // 1. 유형당 하나만 존재하도록 검증
        List<EvaluationElement> existing = elementMapper.findByPeriodId(elementDto.periodId(), elementDto.deptId());
        boolean typeExists = existing.stream()
                .anyMatch(e -> e.getElementTypeCode().equals(elementDto.elementTypeCode()));
        if (typeExists) {
            throw new IllegalStateException("해당 차수 및 부서에 이미 동일한 유형의 평가 항목이 존재합니다.");
        }

        // 2. 가중치 초과 여부 사전 검증 (기존 합 + 신규 항목 가중치)
        BigDecimal currentSum = elementMapper.sumWeightByPeriodId(elementDto.periodId(), elementDto.deptId(), null);
        BigDecimal newTotal = currentSum.add(elementDto.weight());
        if (newTotal.compareTo(WEIGHT_LIMIT) > 0) {
            throw new IllegalStateException(
                    "가중치 합이 100을 초과합니다. 현재 합계: " + currentSum +
                            ", 추가 항목 가중치: " + elementDto.weight() +
                            ", 합산: " + newTotal);
        }

        // 3. 엔티티 변환 및 감사 필드 초기화
        EvaluationElement element = convertToEntity(elementDto);
        element.prePersist();

        // 4. 데이터베이스에 삽입
        elementMapper.insert(element);

        return getElementById(element.getElementId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvaluationElementDTO updateElement(EvaluationElementDTO elementDto) {
        // 1. 유형 중복 검증 (자신을 제외한 다른 항목 중 동일 유형 존재 여부)
        List<EvaluationElement> existing = elementMapper.findByPeriodId(elementDto.periodId(), elementDto.deptId());
        boolean typeExists = existing.stream()
                .anyMatch(e -> !e.getElementId().equals(elementDto.elementId()) && 
                               e.getElementTypeCode().equals(elementDto.elementTypeCode()));
        if (typeExists) {
            throw new IllegalStateException("해당 차수 및 부서에 이미 동일한 유형의 다른 평가 항목이 존재합니다.");
        }

        // 2. 자신을 제외한 가중치 합 + 수정될 가중치가 100을 초과하는지 검증
        BigDecimal otherSum = elementMapper.sumWeightByPeriodId(elementDto.periodId(), elementDto.deptId(), elementDto.elementId());
        BigDecimal newTotal = otherSum.add(elementDto.weight());
        if (newTotal.compareTo(WEIGHT_LIMIT) > 0) {
            throw new IllegalStateException(
                    "가중치 합이 100을 초과합니다. 다른 항목 합계: " + otherSum +
                            ", 수정 항목 가중치: " + elementDto.weight() +
                            ", 합산: " + newTotal);
        }

        // 3. 엔티티 변환 및 업데이트
        EvaluationElement element = convertToEntity(elementDto);
        element.preUpdate();

        int updatedRows = elementMapper.update(element);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("항목 정보가 다른 사용자에 의해 변경되었거나 수정 충돌이 발생했습니다.");
        }
        return getElementById(element.getElementId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteElement(Long elementId) {
        Long currentUserId = 1L;
        int updatedRows = elementMapper.softDelete(elementId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 항목을 찾을 수 없습니다. elementId: " + elementId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean validateWeightSum(Long periodId, Long deptId) {
        // 해당 부서(또는 전사 공통)의 가중치 합계만 확인합니다.
        BigDecimal totalWeight = elementMapper.sumWeightByPeriodId(periodId, deptId, null);
        
        return totalWeight.compareTo(WEIGHT_LIMIT) == 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void resetElements(Long periodId, Long deptId) {
        Long currentUserId = 1L; // TODO: SecurityContext에서 실제 사용자 ID 추출
        elementMapper.resetByPeriodAndDept(periodId, deptId, currentUserId, LocalDateTime.now());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void copyCommonElementsToDept(Long periodId, Long deptId) {
        if (deptId == null) return;

        // 1. 기존 부서 전용 설정 초기화
        resetElements(periodId, deptId);

        // 2. 전사 공통 설정 조회
        List<EvaluationElement> commonElements = elementMapper.findByPeriodId(periodId, null);

        // 3. 공통 설정을 부서 전용으로 복사하여 저장
        for (EvaluationElement common : commonElements) {
            EvaluationElement deptElement = EvaluationElement.builder()
                    .periodId(periodId)
                    .deptId(deptId)
                    .elementTypeCode(common.getElementTypeCode())
                    .elementName(common.getElementName())
                    .maxScore(common.getMaxScore())
                    .weight(common.getWeight())
                    .build();
            deptElement.prePersist();
            elementMapper.insert(deptElement);
        }
    }

    /**
     * 엔티티를 DTO 레코드로 변환합니다.
     */
    private EvaluationElementDTO convertToDto(EvaluationElement element) {
        return EvaluationElementDTO.builder()
                .elementId(element.getElementId())
                .periodId(element.getPeriodId())
                .deptId(element.getDeptId())
                .elementTypeCode(element.getElementTypeCode())
                .elementName(element.getElementName())
                .maxScore(element.getMaxScore())
                .weight(element.getWeight())
                .isDeleted(element.getIsDeleted())
                .version(element.getVersion())
                .createdAt(element.getCreatedAt())
                .createdBy(element.getCreatedBy())
                .updatedAt(element.getUpdatedAt())
                .updatedBy(element.getUpdatedBy())
                .build();
    }

    /**
     * DTO 레코드를 엔티티로 변환합니다.
     */
    private EvaluationElement convertToEntity(EvaluationElementDTO dto) {
        EvaluationElement element = EvaluationElement.builder()
                .elementId(dto.elementId())
                .periodId(dto.periodId())
                .deptId(dto.deptId())
                .elementTypeCode(dto.elementTypeCode())
                .elementName(dto.elementName())
                .maxScore(dto.maxScore())
                .weight(dto.weight())
                .build();
        element.setIsDeleted(dto.isDeleted());
        element.setVersion(dto.version());
        element.setCreatedAt(dto.createdAt());
        element.setCreatedBy(dto.createdBy());
        element.setUpdatedAt(dto.updatedAt());
        element.setUpdatedBy(dto.updatedBy());
        return element;
    }
}

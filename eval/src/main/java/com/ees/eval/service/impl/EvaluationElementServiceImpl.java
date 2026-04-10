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
    public List<EvaluationElementDTO> getElementsByPeriodId(Long periodId) {
        return elementMapper.findByPeriodId(periodId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvaluationElementDTO createElement(EvaluationElementDTO elementDto) {
        // 1. 가중치 초과 여부 사전 검증 (기존 합 + 신규 항목 가중치)
        BigDecimal currentSum = elementMapper.sumWeightByPeriodId(elementDto.periodId(), null);
        BigDecimal newTotal = currentSum.add(elementDto.weight());
        if (newTotal.compareTo(WEIGHT_LIMIT) > 0) {
            throw new IllegalStateException(
                    "가중치 합이 100을 초과합니다. 현재 합계: " + currentSum +
                    ", 추가 항목 가중치: " + elementDto.weight() +
                    ", 합산: " + newTotal);
        }

        // 2. 엔티티 변환 및 감사 필드 초기화
        EvaluationElement element = convertToEntity(elementDto);
        element.prePersist();

        // 3. 데이터베이스에 삽입
        elementMapper.insert(element);

        return getElementById(element.getElementId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvaluationElementDTO updateElement(EvaluationElementDTO elementDto) {
        // 1. 자신을 제외한 가중치 합 + 수정될 가중치가 100을 초과하는지 검증
        BigDecimal otherSum = elementMapper.sumWeightByPeriodId(elementDto.periodId(), elementDto.elementId());
        BigDecimal newTotal = otherSum.add(elementDto.weight());
        if (newTotal.compareTo(WEIGHT_LIMIT) > 0) {
            throw new IllegalStateException(
                    "가중치 합이 100을 초과합니다. 다른 항목 합계: " + otherSum +
                    ", 수정 항목 가중치: " + elementDto.weight() +
                    ", 합산: " + newTotal);
        }

        // 2. 엔티티 변환 및 업데이트
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
    public boolean validateWeightSum(Long periodId) {
        // 해당 차수의 전체 가중치 합이 정확히 100인지 확인
        BigDecimal totalWeight = elementMapper.sumWeightByPeriodId(periodId, null);
        return totalWeight.compareTo(WEIGHT_LIMIT) == 0;
    }

    /**
     * 엔티티를 DTO 레코드로 변환합니다.
     */
    private EvaluationElementDTO convertToDto(EvaluationElement element) {
        return EvaluationElementDTO.builder()
                .elementId(element.getElementId())
                .periodId(element.getPeriodId())
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

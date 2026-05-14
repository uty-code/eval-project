package com.ees.eval.service.impl;

import com.ees.eval.domain.EvaluationTypeWeight;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationTypeWeightDTO;
import com.ees.eval.mapper.EvaluationTypeWeightMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationTypeWeightService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EvaluationTypeWeightServiceImpl implements EvaluationTypeWeightService {

    private final EvaluationTypeWeightMapper weightMapper;
    private final EvaluationElementService elementService;

    public EvaluationTypeWeightServiceImpl(EvaluationTypeWeightMapper weightMapper,
                                           @Lazy EvaluationElementService elementService) {
        this.weightMapper = weightMapper;
        this.elementService = elementService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationTypeWeightDTO> getTypeWeights(Long periodId, Long deptId, String targetRoleCode) {
        List<EvaluationTypeWeight> weights = weightMapper.findByPeriodId(periodId, deptId, targetRoleCode);
        
        // 1. 부서 설정이 없으면 전사 공통 설정(deptId=null)을 폴백(Fallback)으로 가져옴
        if (weights.isEmpty() && deptId != null) {
            weights = weightMapper.findByPeriodId(periodId, null, targetRoleCode);
        }
        
        List<EvaluationTypeWeightDTO> dtoList = weights.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
                
        // 2. 전사 공통 설정조차 없으면, 화면이 깨지지 않도록 기본값 제공
        if (dtoList.isEmpty()) {
            if ("STAFF".equals(targetRoleCode)) {
                dtoList = List.of(
                    EvaluationTypeWeightDTO.builder().elementTypeCode("PERFORMANCE").weight(new BigDecimal("50.00")).build(),
                    EvaluationTypeWeightDTO.builder().elementTypeCode("COMPETENCY").weight(new BigDecimal("50.00")).build()
                );
            } else if ("LEADER".equals(targetRoleCode)) {
                dtoList = List.of(
                    EvaluationTypeWeightDTO.builder().elementTypeCode("MULTI_DIMENSIONAL").weight(new BigDecimal("100.00")).build()
                );
            }
        }
        
        return dtoList;
    }

    @Override
    @Transactional
    public void saveTypeWeights(Long periodId, Long deptId, String targetRoleCode, List<EvaluationTypeWeightDTO> weights) {
        // 합계가 100인지 검증
        BigDecimal total = weights.stream()
                .map(EvaluationTypeWeightDTO::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (total.compareTo(new BigDecimal("100.00")) != 0) {
            throw new IllegalArgumentException(targetRoleCode + " 유형별 가중치 합계는 반드시 100%여야 합니다. (현재: " + total + "%)");
        }

        // 기존 가중치 삭제 (Soft delete)
        weightMapper.deleteByPeriodId(periodId, deptId, targetRoleCode);

        // 신규 가중치 일괄 저장 (Batch)
        if (!weights.isEmpty()) {
            List<EvaluationTypeWeight> entities = weights.stream().map(dto -> {
                EvaluationTypeWeight entity = EvaluationTypeWeight.builder()
                        .periodId(periodId)
                        .deptId(deptId)
                        .targetRoleCode(targetRoleCode)
                        .elementTypeCode(dto.elementTypeCode())
                        .weight(dto.weight())
                        .build();
                entity.prePersist();
                return entity;
            }).collect(Collectors.toList());
            
            weightMapper.insertBatch(entities);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWeightSumValid(Long periodId, Long deptId, String targetRoleCode) {

        // 1) 유형별 가중치(PERFORMANCE/COMPETENCY 비율) 합계 = 100% 검증
        // UI에서 보여지는 기본값(50/50)을 그대로 검증에 사용하기 위해 getTypeWeights 호출
        // 저장버튼을 누르지 않아도 default로 50, 50 설정
        List<EvaluationTypeWeightDTO> rawWeights = getTypeWeights(periodId, deptId, targetRoleCode);

        if (rawWeights.isEmpty()) {
            return false;
        }

        BigDecimal total = rawWeights.stream()
                .map(EvaluationTypeWeightDTO::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("100.00")) != 0) {
            return false;
        }

        // 2) 각 유형별 평가 항목(element) 가중치 합계 = 100% 검증
        List<EvaluationElementDTO> elements;
        if (deptId != null) {
            elements = elementService.getElementsByPeriodId(periodId, deptId);
        } else {
            elements = elementService.getElementsByPeriodId(periodId, null);
        }

        // 유형별 가중치가 설정된 항목에 대해서만 항목 가중치 합계 검증
        for (EvaluationTypeWeightDTO tw : rawWeights) {
            String typeCode = tw.elementTypeCode();
            BigDecimal elementSum = elements.stream()
                    .filter(e -> typeCode.equals(e.elementTypeCode()))
                    .map(EvaluationElementDTO::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // 항목이 하나도 없거나, 합계가 100이 아니면 무효
            boolean hasElements = elements.stream()
                    .anyMatch(e -> typeCode.equals(e.elementTypeCode()));
            
            if (!hasElements || elementSum.compareTo(new BigDecimal("100.00")) != 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCustomConfig(Long periodId, Long deptId) {
        if (deptId == null) {
            return true;
        }
        boolean hasWeights = !weightMapper.findByPeriodId(periodId, deptId, "STAFF").isEmpty();
        boolean hasElements = !elementService.getElementsByPeriodId(periodId, deptId).isEmpty();
        
        return hasWeights || hasElements;
    }

    private EvaluationTypeWeightDTO convertToDto(EvaluationTypeWeight entity) {
        return EvaluationTypeWeightDTO.builder()
                .weightId(entity.getWeightId())
                .periodId(entity.getPeriodId())
                .deptId(entity.getDeptId())
                .targetRoleCode(entity.getTargetRoleCode())
                .elementTypeCode(entity.getElementTypeCode())
                .weight(entity.getWeight())
                .build();
    }
}

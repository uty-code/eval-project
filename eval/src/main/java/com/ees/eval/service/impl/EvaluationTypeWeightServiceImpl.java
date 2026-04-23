package com.ees.eval.service.impl;

import com.ees.eval.domain.EvaluationTypeWeight;
import com.ees.eval.dto.EvaluationTypeWeightDTO;
import com.ees.eval.mapper.EvaluationTypeWeightMapper;
import com.ees.eval.service.EvaluationTypeWeightService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationTypeWeightServiceImpl implements EvaluationTypeWeightService {

    private final EvaluationTypeWeightMapper weightMapper;

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

        // 신규 가중치 저장
        for (EvaluationTypeWeightDTO dto : weights) {
            EvaluationTypeWeight entity = EvaluationTypeWeight.builder()
                    .periodId(periodId)
                    .deptId(deptId)
                    .targetRoleCode(targetRoleCode)
                    .elementTypeCode(dto.elementTypeCode())
                    .weight(dto.weight())
                    .build();
            entity.prePersist();
            weightMapper.insert(entity);
        }
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

package com.ees.eval.service;

import com.ees.eval.domain.EvaluationTypeWeight;
import com.ees.eval.dto.EvaluationTypeWeightDTO;
import com.ees.eval.mapper.EvaluationTypeWeightMapper;
import com.ees.eval.service.impl.EvaluationTypeWeightServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluationTypeWeightService 단위 테스트")
class EvaluationTypeWeightServiceImplTest {

    @Mock
    private EvaluationTypeWeightMapper weightMapper;

    @InjectMocks
    private EvaluationTypeWeightServiceImpl weightService;

    @Test
    @DisplayName("부서별 가중치가 존재할 경우 정상 반환한다")
    void getTypeWeights_ReturnsDeptWeights() {
        // given
        Long periodId = 1L;
        Long deptId = 10L;
        String roleCode = "STAFF";
        
        EvaluationTypeWeight weight = EvaluationTypeWeight.builder()
                .weightId(100L)
                .periodId(periodId)
                .deptId(deptId)
                .targetRoleCode(roleCode)
                .elementTypeCode("PERFORMANCE")
                .weight(new BigDecimal("60.00"))
                .build();
                
        given(weightMapper.findByPeriodId(periodId, deptId, roleCode)).willReturn(List.of(weight));

        // when
        List<EvaluationTypeWeightDTO> result = weightService.getTypeWeights(periodId, deptId, roleCode);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).weight()).isEqualByComparingTo("60.00");
        verify(weightMapper, never()).findByPeriodId(eq(periodId), isNull(), eq(roleCode));
    }

    @Test
    @DisplayName("부서 가중치가 없을 경우 전사 공통 설정을 조회한다 (Fallback 1)")
    void getTypeWeights_FallbackToCompanyWeights() {
        // given
        Long periodId = 1L;
        Long deptId = 10L;
        String roleCode = "STAFF";

        given(weightMapper.findByPeriodId(periodId, deptId, roleCode)).willReturn(Collections.emptyList());
        
        EvaluationTypeWeight companyWeight = EvaluationTypeWeight.builder()
                .weightId(200L)
                .periodId(periodId)
                .deptId(null)
                .targetRoleCode(roleCode)
                .elementTypeCode("PERFORMANCE")
                .weight(new BigDecimal("50.00"))
                .build();
        
        given(weightMapper.findByPeriodId(periodId, null, roleCode)).willReturn(List.of(companyWeight));

        // when
        List<EvaluationTypeWeightDTO> result = weightService.getTypeWeights(periodId, deptId, roleCode);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).weight()).isEqualByComparingTo("50.00");
        verify(weightMapper).findByPeriodId(periodId, null, roleCode);
    }

    @Test
    @DisplayName("전사 설정도 없을 경우 STAFF 기본값을 반환한다 (Fallback 2)")
    void getTypeWeights_DefaultStaffWeights() {
        // given
        Long periodId = 1L;
        Long deptId = 10L;
        String roleCode = "STAFF";

        given(weightMapper.findByPeriodId(any(), any(), any())).willReturn(Collections.emptyList());

        // when
        List<EvaluationTypeWeightDTO> result = weightService.getTypeWeights(periodId, deptId, roleCode);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting("elementTypeCode")
                .containsExactlyInAnyOrder("PERFORMANCE", "COMPETENCY");
        assertThat(result.get(0).weight().add(result.get(1).weight())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("전사 설정도 없을 경우 LEADER 기본값을 반환한다 (Fallback 2)")
    void getTypeWeights_DefaultLeaderWeights() {
        // given
        Long periodId = 1L;
        Long deptId = 10L;
        String roleCode = "LEADER";

        given(weightMapper.findByPeriodId(any(), any(), any())).willReturn(Collections.emptyList());

        // when
        List<EvaluationTypeWeightDTO> result = weightService.getTypeWeights(periodId, deptId, roleCode);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).elementTypeCode()).isEqualTo("MULTI_DIMENSIONAL");
        assertThat(result.get(0).weight()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("가중치 합계가 100%일 경우 정상 저장한다")
    void saveTypeWeights_Success() {
        // given
        Long periodId = 1L;
        Long deptId = 10L;
        String roleCode = "STAFF";
        List<EvaluationTypeWeightDTO> weights = List.of(
            EvaluationTypeWeightDTO.builder().elementTypeCode("PERFORMANCE").weight(new BigDecimal("40.00")).build(),
            EvaluationTypeWeightDTO.builder().elementTypeCode("COMPETENCY").weight(new BigDecimal("60.00")).build()
        );

        // when
        weightService.saveTypeWeights(periodId, deptId, roleCode, weights);

        // then
        verify(weightMapper).deleteByPeriodId(periodId, deptId, roleCode);
        verify(weightMapper, times(2)).insert(any(EvaluationTypeWeight.class));
    }

    @Test
    @DisplayName("가중치 합계가 100%가 아닐 경우 예외를 발생시킨다")
    void saveTypeWeights_Fail_SumNot100() {
        // given
        Long periodId = 1L;
        Long deptId = 10L;
        String roleCode = "STAFF";
        List<EvaluationTypeWeightDTO> weights = List.of(
            EvaluationTypeWeightDTO.builder().elementTypeCode("PERFORMANCE").weight(new BigDecimal("40.00")).build(),
            EvaluationTypeWeightDTO.builder().elementTypeCode("COMPETENCY").weight(new BigDecimal("50.00")).build()
        );

        // when & then
        assertThatThrownBy(() -> weightService.saveTypeWeights(periodId, deptId, roleCode, weights))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가중치 합계는 반드시 100%여야 합니다");
        
        verify(weightMapper, never()).deleteByPeriodId(any(), any(), any());
        verify(weightMapper, never()).insert(any());
    }
}

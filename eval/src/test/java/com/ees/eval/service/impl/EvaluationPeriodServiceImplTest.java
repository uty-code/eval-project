package com.ees.eval.service.impl;

import com.ees.eval.domain.EvaluationPeriod;
import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.MappingAnomalyDTO;
import com.ees.eval.mapper.EvaluationPeriodMapper;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.EvaluatorMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * EvaluationPeriodServiceImpl의 단위 테스트입니다.
 * 평가 시작 전 정합성 검증 로직을 집중적으로 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationPeriodServiceImplTest {

    @Mock
    private EvaluationPeriodMapper periodMapper;

    @Mock
    private EvaluationTypeWeightService typeWeightService;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private EvaluatorMappingService mappingService;

    @Mock
    private com.ees.eval.service.EvaluationGradeRatioService gradeRatioService;

    @InjectMocks
    private EvaluationPeriodServiceImpl periodService;

    private EvaluationPeriod plannedPeriod;

    @BeforeEach
    void setUp() {
        plannedPeriod = EvaluationPeriod.builder()
                .periodId(1L)
                .periodYear(2026)
                .periodName("2026 상반기")
                .statusCode("PLANNED")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();
        plannedPeriod.setVersion(1);
        plannedPeriod.setIsDeleted("n");
    }

    @Test
    @DisplayName("평가 시작 차단 - ERROR 등급 매핑 이상 존재 시 IllegalStateException 발생")
    void transitionStatus_ShouldBlockWhenMappingErrorsExist() {
        // given: PLANNED 상태의 차수, 진행 중 차수 없음
        given(periodMapper.findById(1L)).willReturn(Optional.of(plannedPeriod));
        given(periodMapper.findByStatusCode("IN_PROGRESS")).willReturn(Collections.emptyList());

        // 가중치는 정상
        given(departmentService.getSimpleAllDepartments()).willReturn(List.of(
                DepartmentDTO.builder().deptId(10L).deptName("개발팀").build()
        ));
        given(typeWeightService.isWeightSumValid(1L, 10L, "STAFF")).willReturn(true);
        given(typeWeightService.isWeightSumValid(1L, 10L, "LEADER")).willReturn(true);
        given(gradeRatioService.isGradeRatioValid(1L, 10L)).willReturn(true);

        // 정합성 검사: ERROR 등급 매핑 이상 발견
        List<MappingAnomalyDTO> anomalies = List.of(
                MappingAnomalyDTO.builder()
                        .evaluateeId(100L)
                        .evaluateeName("홍길동")
                        .deptName("개발팀")
                        .anomalyType("MISSING_SELF")
                        .description("본인 평가 매핑이 누락되었습니다.")
                        .severity("ERROR")
                        .build(),
                MappingAnomalyDTO.builder()
                        .evaluateeId(101L)
                        .evaluateeName("김철수")
                        .deptName("개발팀")
                        .anomalyType("MISSING_EXECUTIVE")
                        .description("최종 평가자(임원) 매핑이 누락되었습니다.")
                        .severity("ERROR")
                        .build()
        );
        given(mappingService.checkMappingIntegrity(1L)).willReturn(anomalies);

        // when & then: 예외가 발생하며 에러 메시지에 건수와 대표 사례 포함
        assertThatThrownBy(() -> periodService.transitionStatus(1L, "IN_PROGRESS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2건")
                .hasMessageContaining("홍길동")
                .hasMessageContaining("본인 평가 매핑이 누락");

        // DB 업데이트가 실행되지 않았는지 검증
        verify(periodMapper, never()).update(org.mockito.ArgumentMatchers.any(EvaluationPeriod.class));
    }

    @Test
    @DisplayName("평가 시작 허용 - WARNING만 존재 시 정상적으로 IN_PROGRESS 전이")
    void transitionStatus_ShouldAllowWhenOnlyWarningsExist() {
        // given
        EvaluationPeriod updatedPeriod = EvaluationPeriod.builder()
                .periodId(1L)
                .periodYear(2026)
                .periodName("2026 상반기")
                .statusCode("IN_PROGRESS")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();
        updatedPeriod.setVersion(2);

        // 첫 번째 호출: PLANNED 상태, 두 번째 호출: 업데이트 후 IN_PROGRESS 상태
        given(periodMapper.findById(1L))
                .willReturn(Optional.of(plannedPeriod))
                .willReturn(Optional.of(updatedPeriod));
        given(periodMapper.findByStatusCode("IN_PROGRESS")).willReturn(Collections.emptyList());

        // 가중치 정상
        given(departmentService.getSimpleAllDepartments()).willReturn(List.of(
                DepartmentDTO.builder().deptId(10L).deptName("개발팀").build()
        ));
        given(typeWeightService.isWeightSumValid(1L, 10L, "STAFF")).willReturn(true);
        given(typeWeightService.isWeightSumValid(1L, 10L, "LEADER")).willReturn(true);
        given(gradeRatioService.isGradeRatioValid(1L, 10L)).willReturn(true);

        // 정합성 검사: WARNING만 존재 (다면 평가자 0명인 부서장)
        List<MappingAnomalyDTO> anomalies = List.of(
                MappingAnomalyDTO.builder()
                        .evaluateeId(200L)
                        .evaluateeName("이부장")
                        .deptName("기획팀")
                        .anomalyType("MISSING_SUBORDINATE")
                        .description("다면 평가자(부서원) 매핑이 0명입니다.")
                        .severity("WARNING")
                        .build()
        );
        given(mappingService.checkMappingIntegrity(1L)).willReturn(anomalies);

        // update 성공 mock
        given(periodMapper.update(org.mockito.ArgumentMatchers.any(EvaluationPeriod.class))).willReturn(1);

        // when
        EvaluationPeriodDTO result = periodService.transitionStatus(1L, "IN_PROGRESS");

        // then: 정상 전이 완료
        assertThat(result.statusCode()).isEqualTo("IN_PROGRESS");
        verify(periodMapper).update(org.mockito.ArgumentMatchers.any(EvaluationPeriod.class));
    }

    @Test
    @DisplayName("평가 시작 허용 - 정합성 검사 결과가 깨끗할 때 정상 전이")
    void transitionStatus_ShouldAllowWhenNoAnomalies() {
        // given
        EvaluationPeriod updatedPeriod = EvaluationPeriod.builder()
                .periodId(1L)
                .periodYear(2026)
                .periodName("2026 상반기")
                .statusCode("IN_PROGRESS")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();
        updatedPeriod.setVersion(2);

        // 첫 번째 호출: PLANNED, 두 번째 호출: IN_PROGRESS
        given(periodMapper.findById(1L))
                .willReturn(Optional.of(plannedPeriod))
                .willReturn(Optional.of(updatedPeriod));
        given(periodMapper.findByStatusCode("IN_PROGRESS")).willReturn(Collections.emptyList());

        // 가중치 정상
        given(departmentService.getSimpleAllDepartments()).willReturn(List.of(
                DepartmentDTO.builder().deptId(10L).deptName("개발팀").build()
        ));
        given(typeWeightService.isWeightSumValid(1L, 10L, "STAFF")).willReturn(true);
        given(typeWeightService.isWeightSumValid(1L, 10L, "LEADER")).willReturn(true);
        given(gradeRatioService.isGradeRatioValid(1L, 10L)).willReturn(true);

        // 정합성 검사: 이상 없음
        given(mappingService.checkMappingIntegrity(1L)).willReturn(Collections.emptyList());

        // update 성공 mock
        given(periodMapper.update(org.mockito.ArgumentMatchers.any(EvaluationPeriod.class))).willReturn(1);

        // when
        EvaluationPeriodDTO result = periodService.transitionStatus(1L, "IN_PROGRESS");

        // then
        assertThat(result.statusCode()).isEqualTo("IN_PROGRESS");
        verify(mappingService).checkMappingIntegrity(1L);
    }
}

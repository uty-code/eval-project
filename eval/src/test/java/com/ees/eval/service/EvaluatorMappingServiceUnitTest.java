package com.ees.eval.service;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.EvaluationPeriod;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.enums.EmployeeStatus;
import com.ees.eval.dto.enums.EvaluationPeriodStatus;
import com.ees.eval.dto.enums.SystemRole;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationPeriodMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.repository.EvaluatorMappingJdbcRepository;
import com.ees.eval.service.impl.EvaluatorMappingServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EvaluatorMappingServiceImpl 단위 테스트 (Mockito)
 * <p>
 * autoGenerateMappings 호출 시 EvaluatorMappingJdbcRepository.insertBatch가
 * 올바른 도메인 데이터와 함께 일괄 호출되는지 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluatorMappingService 단위 테스트")
class EvaluatorMappingServiceUnitTest {

    @Mock
    private EvaluatorMappingMapper mappingMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private EvaluationPeriodMapper periodMapper;

    @Mock
    private EvaluatorMappingJdbcRepository evaluatorMappingJdbcRepository;

    @InjectMocks
    private EvaluatorMappingServiceImpl evaluatorMappingService;

    @Test
    @DisplayName("자동 매핑 실행 시 MyBatis foreach 대신 EvaluatorMappingJdbcRepository의 insertBatch가 호출된다")
    void should_call_jdbc_repository_insert_batch_on_auto_mapping() {
        // given
        Long periodId = 1L;
        EvaluationPeriod period = EvaluationPeriod.builder()
                .periodId(periodId)
                .statusCode(EvaluationPeriodStatus.PLANNED.getCode())
                .build();

        when(periodMapper.findById(periodId)).thenReturn(Optional.of(period));

        Employee emp1 = Employee.builder()
                .empId(101L)
                .name("사원1")
                .deptId(10L)
                .statusCode(EmployeeStatus.EMPLOYED.getCode())
                .build();

        when(employeeMapper.findAll()).thenReturn(List.of(emp1));
        when(departmentMapper.findAll()).thenReturn(List.of());
        when(employeeMapper.findRoleNamesByEmpIds(any())).thenReturn(List.of(
                Map.of("EMP_ID", 101L, "ROLE_NAME", SystemRole.ROLE_USER.getCode())
        ));
        when(mappingMapper.findAllByPeriodId(periodId)).thenReturn(List.of());

        // when
        int createdCount = evaluatorMappingService.autoGenerateMappings(periodId, null, null);

        // then
        assertThat(createdCount).isGreaterThan(0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvaluatorMapping>> captor = ArgumentCaptor.forClass(List.class);

        verify(evaluatorMappingJdbcRepository, times(1)).insertBatch(captor.capture());
        verify(mappingMapper, never()).insertBatch(any());

        List<EvaluatorMapping> insertedList = captor.getValue();
        assertThat(insertedList).hasSize(createdCount);
        assertThat(insertedList.get(0).getEvaluateeId()).isEqualTo(101L);
    }
}

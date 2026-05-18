package com.ees.eval.support.ui;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EvalFilterConfig;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EvaluationPeriodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link EvalFilterConfigFactory}의 단위 테스트 클래스입니다.
 * 각 평가 모듈에 맞는 필터 설정 객체(EvalFilterConfig)가 요구사항에 맞게 정상 빌드되는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
public class EvalFilterConfigFactoryTest {

    @Mock
    private DepartmentService departmentService;

    @Mock
    private EvaluationPeriodService periodService;

    @InjectMocks
    private EvalFilterConfigFactory factory;

    private List<EvaluationPeriodDTO> mockPeriods;
    private List<DepartmentDTO> mockDepts;

    @BeforeEach
    void setUp() {
        mockPeriods = List.of(
            EvaluationPeriodDTO.builder()
                .periodId(1L)
                .periodYear(2026)
                .periodName("상반기 종합평가")
                .statusCode("IN_PROGRESS")
                .isDeleted("n")
                .build(),
            EvaluationPeriodDTO.builder()
                .periodId(2L)
                .periodYear(2026)
                .periodName("하반기 종합평가")
                .statusCode("PLANNED")
                .isDeleted("n")
                .build()
        );
        mockDepts = List.of(
            DepartmentDTO.builder()
                .deptId(1L)
                .deptName("개발부")
                .treeDepth(1)
                .build()
        );
    }

    /**
     * 어드민 권한일 때, 나의 평가 필터 설정이 관리자 조건에 맞게 올바르게 생성되는지 검증합니다.
     */
    @Test
    void should_createMyEvalConfig_when_admin() {
        // given
        when(periodService.getAllPeriods()).thenReturn(mockPeriods);
        when(periodService.resolveSelectedPeriod(eq(1L), any())).thenReturn(mockPeriods.getFirst());
        when(departmentService.getAllDepartments()).thenReturn(mockDepts);

        // when
        EvalFilterConfig config = factory.createMyEvalConfig(1L, "WAITING", "홍길동", 1L, true);

        // then
        assertThat(config.actionUrl()).isEqualTo("/eval/my-evaluation/list");
        assertThat(config.deptFilter().show()).isTrue();
        assertThat(config.deptFilter().departments()).hasSize(1);
        assertThat(config.keywordFilter().placeholder()).isEqualTo("이름 또는 사번 입력");
    }

    /**
     * 일반 사용자 권한일 때, 나의 평가 필터 설정이 일반 사용자 조건에 맞게 올바르게 생성되는지 검증합니다.
     */
    @Test
    void should_createMyEvalConfig_when_normalUser() {
        // given
        when(periodService.getAllPeriods()).thenReturn(mockPeriods);
        when(periodService.resolveSelectedPeriod(eq(1L), any())).thenReturn(mockPeriods.getFirst());

        // when
        EvalFilterConfig config = factory.createMyEvalConfig(1L, "WAITING", "차수", 1L, false);

        // then
        assertThat(config.actionUrl()).isEqualTo("/eval/my-evaluation/list");
        assertThat(config.deptFilter().show()).isFalse();
        assertThat(config.deptFilter().departments()).isEmpty();
        assertThat(config.keywordFilter().placeholder()).isEqualTo("차수 이름 입력");
    }

    /**
     * 다면평가 모듈용 필터 설정 객체가 올바르게 생성되는지 검증합니다.
     */
    @Test
    void should_createMultiDimensionalConfig() {
        // given
        when(periodService.getAllPeriods()).thenReturn(mockPeriods);
        when(periodService.resolveSelectedPeriod(eq(1L), any())).thenReturn(mockPeriods.getFirst());
        when(departmentService.getAllDepartments()).thenReturn(mockDepts);

        // when
        EvalFilterConfig config = factory.createMultiDimensionalConfig(1L, "WAITING", "홍길동", 1L);

        // then
        assertThat(config.actionUrl()).isEqualTo("/eval/multi-dimensional");
        assertThat(config.deptFilter().show()).isTrue();
        assertThat(config.statusFilter().show()).isTrue();
    }
}

package com.ees.eval.support.ui;

import com.ees.eval.dto.EvalFilterConfig;
import com.ees.eval.dto.FilterOption;
import com.ees.eval.dto.HiddenParam;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EvaluationPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 각 평가 모듈의 화면 요구사항에 따라 일치하는 {@link EvalFilterConfig} 객체를 합성(Composition)하고
 * 비즈니스 서비스로부터 필요한 데이터(부서 트리, 평가 차수)를 안전하게 조립해주는 전용 UI 팩토리 컴포넌트입니다.
 */
@Component
@RequiredArgsConstructor
public class EvalFilterConfigFactory {

    private final DepartmentService departmentService;
    private final EvaluationPeriodService periodService;

    /**
     * 차수 목록의 정렬 우선순위 규칙을 적용하여 반환합니다.
     * 진행 중(IN_PROGRESS)인 차수가 최상단에 오고, 그 외에는 연도 및 ID 내림차순(최신순)으로 정렬합니다.
     */
    private List<EvaluationPeriodDTO> getSortedPeriods() {
        List<EvaluationPeriodDTO> periods = new ArrayList<>(periodService.getAllPeriods());
        periods.sort((p1, p2) -> {
            boolean p1Active = "IN_PROGRESS".equals(p1.statusCode());
            boolean p2Active = "IN_PROGRESS".equals(p2.statusCode());
            if (p1Active && !p2Active) return -1;
            if (!p1Active && p2Active) return 1;

            int yearCompare = p2.periodYear().compareTo(p1.periodYear());
            if (yearCompare != 0) return yearCompare;

            return p2.periodId().compareTo(p1.periodId());
        });
        return periods;
    }

    /**
     * 나의 자가평가(My Evaluation) 화면을 위한 필터 설정을 빌드합니다.
     */
    public EvalFilterConfig createMyEvalConfig(Long periodId, String filterStatus, String keyword, Long filterDeptId, boolean isAdmin) {
        List<EvaluationPeriodDTO> periods = getSortedPeriods();
        EvaluationPeriodDTO selected = periodService.resolveSelectedPeriod(periodId != null && periodId > 0 ? periodId : null, periods);
        Long selectedId = (periodId != null && periodId == 0L) ? 0L : (selected != null ? selected.periodId() : 0L);

        List<FilterOption> statusOptions = List.of(
            new FilterOption("WAITING", "대기"),
            new FilterOption("SUBMITTED", "완료")
        );

        // 어드민인 경우에만 부서 트리를 노출하고, 일반 사용자는 조회 제한 정책을 따릅니다.
        List<DepartmentDTO> departments = isAdmin ? departmentService.getAllDepartments() : Collections.emptyList();

        return EvalFilterConfig.builder()
            .actionUrl("/eval/my-evaluation/list")
            .htmxTarget("#my-eval-container")
            .htmxSelect("#my-eval-container")
            .periodFilter(new EvalFilterConfig.PeriodFilter(true, periods, selectedId, true))
            .deptFilter(new EvalFilterConfig.DeptFilter(isAdmin, departments, filterDeptId))
            .statusFilter(new EvalFilterConfig.StatusFilter(true, statusOptions, filterStatus))
            .keywordFilter(new EvalFilterConfig.KeywordFilter(true, keyword, isAdmin ? "이름 또는 사번 입력" : "차수 이름 입력"))
            .showReset(true)
            .build();
    }

    /**
     * 다면평가(Multi-Dimensional Evaluation) 화면을 위한 필터 설정을 빌드합니다.
     */
    public EvalFilterConfig createMultiDimensionalConfig(Long periodId, String filterStatus, String keyword, Long filterDeptId) {
        List<EvaluationPeriodDTO> periods = getSortedPeriods();
        EvaluationPeriodDTO selected = periodService.resolveSelectedPeriod(periodId != null && periodId > 0 ? periodId : null, periods);
        Long selectedId = (periodId != null && periodId == 0L) ? 0L : (selected != null ? selected.periodId() : 0L);

        List<FilterOption> statusOptions = List.of(
            new FilterOption("WAITING", "대기"),
            new FilterOption("SUBMITTED", "완료")
        );

        return EvalFilterConfig.builder()
            .actionUrl("/eval/multi-dimensional")
            .htmxTarget("#eval-table-container")
            .htmxSelect("#eval-table-container")
            .periodFilter(new EvalFilterConfig.PeriodFilter(true, periods, selectedId, true))
            .deptFilter(new EvalFilterConfig.DeptFilter(true, departmentService.getAllDepartments(), filterDeptId))
            .statusFilter(new EvalFilterConfig.StatusFilter(true, statusOptions, filterStatus))
            .keywordFilter(new EvalFilterConfig.KeywordFilter(true, keyword, "이름 또는 사번 입력"))
            .showReset(true)
            .build();
    }

    /**
     * 성과/역량평가(Performance Evaluation) 화면을 위한 필터 설정을 빌드합니다.
     */
    public EvalFilterConfig createPerformanceConfig(Long periodId, String filterStatus, String keyword, Long filterDeptId) {
        List<EvaluationPeriodDTO> periods = getSortedPeriods();
        EvaluationPeriodDTO selected = periodService.resolveSelectedPeriod(periodId != null && periodId > 0 ? periodId : null, periods);
        Long selectedId = (periodId != null && periodId == 0L) ? 0L : (selected != null ? selected.periodId() : 0L);

        List<FilterOption> statusOptions = List.of(
            new FilterOption("대기", "대기"),
            new FilterOption("완료", "완료")
        );

        return EvalFilterConfig.builder()
            .actionUrl("/eval/performance")
            .htmxTarget("#eval-table-container")
            .htmxSelect("#eval-table-container")
            .periodFilter(new EvalFilterConfig.PeriodFilter(true, periods, selectedId, true))
            .deptFilter(new EvalFilterConfig.DeptFilter(true, departmentService.getAllDepartments(), filterDeptId))
            .statusFilter(new EvalFilterConfig.StatusFilter(true, statusOptions, filterStatus))
            .keywordFilter(new EvalFilterConfig.KeywordFilter(true, keyword, "이름 또는 사번 입력"))
            .showReset(true)
            .build();
    }

    /**
     * 최종 등급 확정(Final Grade) 화면을 위한 필터 설정을 빌드합니다.
     */
    public EvalFilterConfig createFinalGradeConfig(Long periodId, String filterStatus, String keyword, Long filterDeptId, String tab) {
        List<EvaluationPeriodDTO> periods = getSortedPeriods();
        // periodId가 0(전체)인 경우 null로 판단하여 resolve
        EvaluationPeriodDTO selected = periodService.resolveSelectedPeriod(periodId != null && periodId > 0 ? periodId : null, periods);
        Long selectedId = selected != null ? selected.periodId() : 0L;

        List<FilterOption> statusOptions = List.of(
            new FilterOption("WAIT", "대기"),
            new FilterOption("DONE", "완료")
        );

        List<HiddenParam> hiddenParams = List.of(new HiddenParam("tab", tab));

        return EvalFilterConfig.builder()
            .actionUrl("/eval/final-grade")
            .htmxTarget("#page-content")
            .htmxSelect("#page-content")
            .periodFilter(new EvalFilterConfig.PeriodFilter(true, periods, selectedId, true))
            .deptFilter(new EvalFilterConfig.DeptFilter(true, departmentService.getAllDepartments(), filterDeptId))
            .statusFilter(new EvalFilterConfig.StatusFilter(true, statusOptions, filterStatus))
            .keywordFilter(new EvalFilterConfig.KeywordFilter(true, keyword, "사번 또는 성명 입력"))
            .hiddenParams(hiddenParams)
            .showReset(false)
            .build();
    }

    /**
     * 평가 결과 조회(Evaluation Result) 화면을 위한 필터 설정을 빌드합니다.
     */
    public EvalFilterConfig createResultConfig(Long periodId, String keyword, Long filterDeptId, List<DepartmentDTO> filteredDepts) {
        // 평가 준비 중(PLANNED) 단계를 제외한 완료된 평가 차수들만 조회
        List<EvaluationPeriodDTO> periods = getSortedPeriods().stream()
            .filter(p -> !"PLANNED".equals(p.statusCode()))
            .toList();
        
        EvaluationPeriodDTO selected = periodService.resolveSelectedPeriod(periodId != null && periodId > 0 ? periodId : null, periods);
        Long selectedId = (periodId != null && periodId == 0L) ? 0L : (selected != null ? selected.periodId() : 0L);

        return EvalFilterConfig.builder()
            .actionUrl("/eval/result")
            .htmxTarget("#page-content")
            .htmxSelect("#page-content")
            .periodFilter(new EvalFilterConfig.PeriodFilter(true, periods, selectedId, true))
            .deptFilter(new EvalFilterConfig.DeptFilter(true, filteredDepts, filterDeptId))
            .statusFilter(new EvalFilterConfig.StatusFilter(false, Collections.emptyList(), null)) // 결과는 상태 필터가 없음
            .keywordFilter(new EvalFilterConfig.KeywordFilter(true, keyword, "사번 또는 성명 입력"))
            .showReset(false)
            .build();
    }
}

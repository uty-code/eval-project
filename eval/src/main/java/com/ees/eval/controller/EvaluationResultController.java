package com.ees.eval.controller;

import com.ees.eval.domain.Department;
import com.ees.eval.domain.Employee;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluationResultDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluationResultService;
import com.ees.eval.service.EvaluationReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 평가 결과 현황 컨트롤러입니다.
 * 최종 확정(EXECUTIVE 제출)이 완료된 사원의 유형별 1차/2차/최종 점수와 등급을 조회합니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/result")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EXECUTIVE')")
public class EvaluationResultController {

    private final EvaluationPeriodService periodService;
    private final EvaluationResultService resultService;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final EvaluationReportService reportService;
    private final com.ees.eval.service.EvaluationGradeRatioService gradeRatioService;
    private final com.ees.eval.support.ui.EvalFilterConfigFactory filterConfigFactory;

    /**
     * 평가 결과 현황 메인 페이지를 조회합니다.
     *
     * @param periodId    평가 차수 ID (선택)
     * @param filterDeptId 부서 필터 ID (선택)
     * @param deptId      부서 필터 ID (선택) (Fallback)
     * @param keyword     검색 키워드 (선택)
     * @param search      검색 키워드 (선택) (Fallback)
     * @param userDetails 로그인 사용자
     * @param model       뷰 모델
     * @return 평가 결과 현황 뷰 경로
     */
    @GetMapping
    public String list(@RequestParam(name = "periodId", required = false) Long periodId,
                       @RequestParam(name = "filterDeptId", required = false) Long filterDeptId,
                       @RequestParam(name = "deptId", required = false) Long deptId, // fallback
                       @RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "search", required = false) String search, // fallback
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model) {

        model.addAttribute("activeMenu", "eval-result");
        Long loginEmpId = Long.parseLong(userDetails.getUsername());

        // Fallback 호환 레이어 적용
        Long resolvedDeptId = filterDeptId != null ? filterDeptId : deptId;
        String resolvedKeyword = keyword != null ? keyword : search;

        // 1. 차수 목록 조회 (PLANNED 제외)
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods().stream()
                .filter(p -> !"PLANNED".equals(p.statusCode()))
                .collect(Collectors.toList());
        model.addAttribute("periods", periods);

        EvaluationPeriodDTO selectedPeriod;
        if (periodId != null && periodId == 0L) {
            selectedPeriod = EvaluationPeriodDTO.builder()
                    .periodId(0L)
                    .periodName("전체 차수 통합")
                    .periodYear(0)
                    .statusCode("IN_PROGRESS")
                    .startDate(java.time.LocalDate.now())
                    .endDate(java.time.LocalDate.now())
                    .build();
        } else {
            selectedPeriod = periodService.resolveSelectedPeriod(periodId, periods);
        }
        model.addAttribute("selectedPeriod", selectedPeriod);

        if (selectedPeriod == null) {
            model.addAttribute("infoMessage", "진행 중인 평가 차수가 없습니다.");
            return "eval/result/list";
        }

        // 2. 부서 목록 (필터용) — 관리자/임원은 전체, 부서장은 자기 부서만
        List<Department> departments = departmentMapper.findAll();
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isExecutive = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EXECUTIVE"));

        List<com.ees.eval.dto.DepartmentDTO> deptDtos = new java.util.ArrayList<>();
        if (isAdmin || isExecutive) {
            deptDtos = departments.stream()
                    .map(d -> com.ees.eval.dto.DepartmentDTO.builder()
                            .deptId(d.getDeptId())
                            .parentDeptId(d.getParentDeptId())
                            .deptName(d.getDeptName())
                            .build())
                    .collect(Collectors.toList());
            model.addAttribute("departments", deptDtos);
        } else {
            Employee loginEmp = employeeMapper.findById(loginEmpId).orElse(null);
            if (loginEmp != null) {
                resolvedDeptId = loginEmp.getDeptId();
                final Long managerDeptId = resolvedDeptId;
                deptDtos = departments.stream()
                        .filter(d -> d.getDeptId().equals(managerDeptId))
                        .map(d -> com.ees.eval.dto.DepartmentDTO.builder()
                                .deptId(d.getDeptId())
                                .parentDeptId(d.getParentDeptId())
                                .deptName(d.getDeptName())
                                .build())
                        .collect(Collectors.toList());
                model.addAttribute("departments", deptDtos);
            }
        }
        model.addAttribute("selectedDeptId", resolvedDeptId);
        model.addAttribute("search", resolvedKeyword);

        // 3. 서비스 호출 — 핵심 로직 위임
        List<EvaluationResultDTO> results = resultService.getResults(selectedPeriod.periodId(), resolvedDeptId, resolvedKeyword);

        if (results.isEmpty()) {
            model.addAttribute("infoMessage", "평가 매핑이 설정되지 않았습니다.");
            model.addAttribute("filterConfig", filterConfigFactory.createResultConfig(selectedPeriod.periodId(), resolvedKeyword, resolvedDeptId, deptDtos));
            return "eval/result/list";
        }

        model.addAttribute("results", results);

        // 4. 등급 분포 통계 (최종 확정된 건만 집계)
        Map<String, Long> gradeDistribution = results.stream()
                .filter(r -> r.isConfirmed() && r.gradeCode() != null && !r.gradeCode().trim().isEmpty())
                .collect(Collectors.groupingBy(EvaluationResultDTO::gradeCode, Collectors.counting()));
        
        long gradedCount = gradeDistribution.values().stream().mapToLong(Long::longValue).sum();

        model.addAttribute("gradeDistribution", gradeDistribution);
        model.addAttribute("totalCount", results.size());
        model.addAttribute("gradedCount", gradedCount);

        // 5. 탭별 카운트
        long staffCount = results.stream().filter(r -> !r.isLeader()).count();
        long leaderCount = results.stream().filter(EvaluationResultDTO::isLeader).count();
        model.addAttribute("staffCount", staffCount);
        model.addAttribute("leaderCount", leaderCount);

        // 6. 부서 선택 및 단일 차수일 때에만 상대평가 비율(gradeRatio)을 뷰로 전달
        if (resolvedDeptId != null && selectedPeriod.periodId() != 0L) {
            com.ees.eval.dto.EvaluationGradeRatioDTO ratio = gradeRatioService.getGradeRatio(selectedPeriod.periodId(), resolvedDeptId);
            model.addAttribute("gradeRatio", ratio);
        }

        // 공통 필터 바 구성 DTO 전달
        model.addAttribute("filterConfig", filterConfigFactory.createResultConfig(selectedPeriod.periodId(), resolvedKeyword, resolvedDeptId, deptDtos));

        return "eval/result/list";
    }

    /**
     * 평가 결과 현황을 엑셀 파일로 다운로드합니다.
     */
    @GetMapping("/excel")
    public void downloadExcel(@RequestParam(name = "periodId", required = false) Long periodId,
                              @RequestParam(name = "filterDeptId", required = false) Long filterDeptId,
                              @RequestParam(name = "deptId", required = false) Long deptId, // fallback
                              @RequestParam(name = "keyword", required = false) String keyword,
                              @RequestParam(name = "search", required = false) String search, // fallback
                              @AuthenticationPrincipal UserDetails userDetails,
                              HttpServletResponse response) throws IOException {

        Long resolvedDeptId = filterDeptId != null ? filterDeptId : deptId;
        String resolvedKeyword = keyword != null ? keyword : search;

        log.info("Excel download requested: periodId={}, deptId={}", periodId, resolvedDeptId);

        // 1. 차수 결정 로직 개선
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods().stream()
                .filter(p -> !"PLANNED".equals(p.statusCode()))
                .collect(Collectors.toList());

        EvaluationPeriodDTO selectedPeriod;
        if (periodId != null && periodId == 0L) {
            selectedPeriod = EvaluationPeriodDTO.builder()
                    .periodId(0L)
                    .periodName("전체 차수 통합")
                    .periodYear(0)
                    .statusCode("IN_PROGRESS")
                    .startDate(java.time.LocalDate.now())
                    .endDate(java.time.LocalDate.now())
                    .build();
        } else {
            selectedPeriod = periodService.resolveSelectedPeriod(periodId, periods);
        }

        if (selectedPeriod == null) {
            log.warn("No evaluation period found for excel download. periodId={}", periodId);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "유효한 평가 차수를 찾을 수 없습니다.");
            return;
        }

        // 2. 결과 데이터 조회
        List<EvaluationResultDTO> results = resultService.getResults(selectedPeriod.periodId(), resolvedDeptId, resolvedKeyword);

        // 3. 프리미엄 리포트 서비스 호출 (Excel 생성 및 스트림 출력)
        reportService.generatePremiumReport(selectedPeriod, results, response);
    }
}

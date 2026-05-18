package com.ees.eval.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.domain.FinalGrade;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.FinalGradeMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluationService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.EvaluatorMappingService;
import com.ees.eval.service.FinalGradeService;
import com.ees.eval.dto.FinalGradeSearchCondition;
import com.ees.eval.dto.FinalGradeTaskDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.dto.DepartmentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/eval/final-grade")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
public class FinalGradeController {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final EvaluationService evaluationService;
    private final EvaluationMapper evaluationMapper;
    private final EvaluatorMappingMapper evaluatorMappingMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final FinalGradeService finalGradeService;
    private final FinalGradeMapper finalGradeMapper;
    private final com.ees.eval.service.ScoreCalculationService scoreCalculationService;



    @GetMapping
    public String list(@ModelAttribute FinalGradeSearchCondition condition,
                       @AuthenticationPrincipal UserDetails userDetails,
                       Model model) {
        
        String tab = condition.tab() != null ? condition.tab() : "leader";
        Long periodId = condition.periodId();
        
        model.addAttribute("activeMenu", "final-grade");
        model.addAttribute("activeTab", tab);
        Long executiveEmpId = Long.parseLong(userDetails.getUsername());

        // 어드민 여부 판별
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("isAdminView", isAdmin);
        
        // 1. 차수 목록 조회
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        
        // 진행중(IN_PROGRESS)인 차수가 최상단에 오고, 그 외에는 연도 및 ID 내림차순(최신순)으로 정렬
        periods.sort((p1, p2) -> {
            boolean p1Prog = "IN_PROGRESS".equals(p1.statusCode());
            boolean p2Prog = "IN_PROGRESS".equals(p2.statusCode());
            if (p1Prog && !p2Prog) return -1;
            if (!p1Prog && p2Prog) return 1;
            
            int yearCompare = Integer.compare(p2.periodYear(), p1.periodYear());
            if (yearCompare != 0) return yearCompare;
            
            return Long.compare(p2.periodId(), p1.periodId());
        });
        
        model.addAttribute("periods", periods);

        // 2. 선택된 차수 처리 (periodId가 0이면 '전체 차수', null이면 초기 진입으로 보고 자동 선택)
        EvaluationPeriodDTO selectedPeriod = null;
        if (periodId != null) {
            if (periodId != 0L) {
                selectedPeriod = periodService.resolveSelectedPeriod(periodId, periods);
            }
            // 0인 경우 selectedPeriod는 null 유지 (전체 차수)
        } else {
            // 초기 진입 시에만 진행 중인 차수 자동 선택
            selectedPeriod = periodService.resolveSelectedPeriod(null, periods);
        }
        model.addAttribute("selectedPeriod", selectedPeriod);

        // 3. 부서 목록 조회 (필터 드롭다운용)
        List<DepartmentDTO> departments = departmentMapper.findAll().stream()
                .map(d -> DepartmentDTO.builder()
                        .deptId(d.getDeptId())
                        .deptName(d.getDeptName())
                        .parentDeptId(d.getParentDeptId())
                        .build())
                .collect(Collectors.toList());
        model.addAttribute("departments", departments);
        model.addAttribute("condition", condition);

        // 4. 데이터 조회 (전체 차수이거나 특정 차수가 선택된 경우)
        Long activePeriodId = (selectedPeriod != null) ? selectedPeriod.periodId() : null;
        
        if (selectedPeriod != null && "PLANNED".equals(selectedPeriod.statusCode())) {
            // PLANNED 차수인 경우 템플릿의 카드 배너만 활용하고 상단 infoMessage는 노출하지 않습니다.
        } else {
            // [최적화] FinalGradeService를 통해 벌크 조회 및 상태 플래그 계산
            FinalGradeSearchCondition activeCondition = new FinalGradeSearchCondition(
                    activePeriodId, 
                    condition.deptId(), 
                    condition.search(), 
                    tab,
                    condition.status()
            );

            List<FinalGradeTaskDTO> tasks;
            if (isAdmin) {
                // 어드민: 임원 필터 없이 전체 최종 등급 대상 조회
                tasks = finalGradeService.getAdminFinalGradeTasks(activeCondition);
            } else {
                // 일반 임원: 기존 로직
                tasks = finalGradeService.getFinalGradeTasks(executiveEmpId, activeCondition);
            }
            model.addAttribute("tasks", tasks);
            
            // 인원수 미리 계산
            long leaderCount = tasks.stream().filter(FinalGradeTaskDTO::isLeader).count();
            long staffCount = tasks.stream().filter(t -> !t.isLeader()).count();
            model.addAttribute("leaderCount", leaderCount);
            model.addAttribute("staffCount", staffCount);
            
        }

        return "eval/final-grade/list";
    }

    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId,
                          Model model,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes redirectAttributes) {

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("isAdminView", isAdmin);

        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);

        EvaluationPeriodDTO period = periodService.getPeriodById(mapping.periodId());
        if ("PLANNED".equals(period.statusCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 시작 전입니다.");
            return "redirect:/eval/final-grade?periodId=" + mapping.periodId();
        }

        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;

        boolean isLeader = departmentMapper.countDepartmentsByLeaderId(mapping.evaluateeId()) > 0;
        String targetRoleCode = isLeader ? "LEADER" : "STAFF";

        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluateeDeptId, targetRoleCode)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "유형별 가중치 합계가 100%가 아닙니다. 관리자에게 가중치 설정을 요청하세요.");
            return "redirect:/eval/final-grade?periodId=" + mapping.periodId();
        }

        model.addAttribute("mapping", mapping);
        model.addAttribute("activeMenu", "final-grade");
        model.addAttribute("mappingId", mappingId);
        model.addAttribute("isLeader", isLeader);

        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
        
        List<EvaluationElementDTO> performanceElements = isLeader ? List.of() : allElements.stream()
            .filter(e -> "PERFORMANCE".equals(e.elementTypeCode()))
            .toList();
        List<EvaluationElementDTO> competencyElements = allElements.stream()
            .filter(e -> isLeader ? "MULTI_DIMENSIONAL".equals(e.elementTypeCode()) : "COMPETENCY".equals(e.elementTypeCode()))
            .toList();

        model.addAttribute("performanceElements", performanceElements);
        model.addAttribute("competencyElements", competencyElements);

        // 기존 작성 내용
        Map<Long, Evaluation> savedMap = evaluationMapper
            .findByMappingId(mappingId)
            .stream()
            .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));
        model.addAttribute("savedMap", savedMap);

        // 자가평가 내용 참조
        Map<Long, Evaluation> selfEvalMap = evaluatorMappingMapper
            .findByEvaluateeId(mapping.periodId(), mapping.evaluateeId())
            .stream()
            .filter(m -> "SELF".equals(m.getRelationTypeCode()))
            .findFirst()
            .map(selfMapping -> evaluationMapper.findByMappingId(selfMapping.getMappingId())
                .stream()
                .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a)))
            .orElse(java.util.Collections.emptyMap());
        model.addAttribute("selfEvalMap", selfEvalMap);

        // 1차 평가자(MANAGER) 내용 참조
        Map<Long, Evaluation> managerEvalMap = evaluatorMappingMapper
            .findByEvaluateeId(mapping.periodId(), mapping.evaluateeId())
            .stream()
            .filter(m -> "MANAGER".equals(m.getRelationTypeCode()))
            .findFirst()
            .map(managerMapping -> evaluationMapper.findByMappingId(managerMapping.getMappingId())
                .stream()
                .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a)))
            .orElse(java.util.Collections.emptyMap());
        model.addAttribute("managerEvalMap", managerEvalMap);

        log.info("[FinalGrade] evaluateeId={}, selfEvalMap keys={}, managerEvalMap keys={}",
                 mapping.evaluateeId(), selfEvalMap.keySet(), managerEvalMap.keySet());

        // 아직 작성 내역이 없으면 MANAGER 내용으로 기본 세팅
        if (savedMap.isEmpty() && !managerEvalMap.isEmpty()) {
            savedMap.putAll(managerEvalMap);
        }
        
        // 모든 항목이 제출되었는지 확인
        Set<Long> targetIds = allElements.stream()
            .filter(e -> isLeader ? "MULTI_DIMENSIONAL".equals(e.elementTypeCode()) : 
                         ("PERFORMANCE".equals(e.elementTypeCode()) || "COMPETENCY".equals(e.elementTypeCode())))
            .map(EvaluationElementDTO::elementId)
            .collect(Collectors.toSet());
        
        boolean submitted = !targetIds.isEmpty() && savedMap.entrySet().stream()
            .filter(entry -> targetIds.contains(entry.getKey()))
            .allMatch(entry -> "SUBMITTED".equals(entry.getValue().getConfirmStatusCode()));
        model.addAttribute("submitted", submitted);

        // 부서장(LEADER)인 경우 다면평가 상세 데이터 수집 (디자인 구현용)
        if (isLeader) {
            // [N+1 수정] getMappingById 루프 제거 — findByEvaluateeId 결과를 직접 사용
            List<com.ees.eval.domain.EvaluatorMapping> allMappings = evaluatorMappingMapper
                    .findByEvaluateeId(mapping.periodId(), mapping.evaluateeId());

            // SUBORDINATE 매핑 ID만 추출 후 평가 데이터 벌크 조회
            List<Long> subMappingIds = allMappings.stream()
                    .filter(m -> "SUBORDINATE".equals(m.getRelationTypeCode()))
                    .map(com.ees.eval.domain.EvaluatorMapping::getMappingId)
                    .collect(Collectors.toList());

            Map<Long, List<Evaluation>> subEvalGroupMap = new HashMap<>();
            if (!subMappingIds.isEmpty()) {
                evaluationMapper.findByMappingIds(subMappingIds).forEach(
                        e -> subEvalGroupMap.computeIfAbsent(e.getMappingId(), k -> new java.util.ArrayList<>()).add(e));
            }

            Map<Long, com.ees.eval.domain.EvaluatorMapping> subMappingMap = allMappings.stream()
                    .filter(m -> "SUBORDINATE".equals(m.getRelationTypeCode()))
                    .collect(Collectors.toMap(com.ees.eval.domain.EvaluatorMapping::getMappingId, m -> m));

            Map<Long, List<Map<String, Object>>> multiEvalDataMap = new HashMap<>();
            Map<Long, Double> multiEvalAvgMap = new HashMap<>();

            for (Long subMappingId : subMappingIds) {
                com.ees.eval.domain.EvaluatorMapping subMapping = subMappingMap.get(subMappingId);
                String evaluatorName = subMapping.getEvaluatorName() != null ? subMapping.getEvaluatorName() : "";
                List<Evaluation> subEvals = subEvalGroupMap.getOrDefault(subMappingId, java.util.Collections.emptyList());
                for (Evaluation eval : subEvals) {
                    if ("SUBMITTED".equals(eval.getConfirmStatusCode())) {
                        Map<String, Object> evalInfo = new HashMap<>();
                        evalInfo.put("evaluatorName", evaluatorName);
                        evalInfo.put("score", eval.getScore() != null ? eval.getScore() : 0);
                        evalInfo.put("comment", eval.getReason() != null ? eval.getReason() : "");
                        multiEvalDataMap.computeIfAbsent(eval.getElementId(), k -> new java.util.ArrayList<>()).add(evalInfo);
                    }
                }
            }

            // 평균 점수 계산 로직을 Java단으로 이동
            multiEvalDataMap.forEach((elementId, evals) -> {
                double avg = evals.stream()
                    .mapToDouble(e -> ((Integer) e.get("score")).doubleValue())
                    .average()
                    .orElse(0.0);
                multiEvalAvgMap.put(elementId, avg);
            });

            model.addAttribute("multiEvalDataMap", multiEvalDataMap);
            model.addAttribute("multiEvalAvgMap", multiEvalAvgMap);

            // 부서원(SUBORDINATE) 다면평가 전원 제출 여부 계산
            int subTotal = subMappingIds.size();
            int subSubmittedCount = 0;
            for (Long subMappingId : subMappingIds) {
                List<Evaluation> subEvals = subEvalGroupMap.getOrDefault(subMappingId, java.util.Collections.emptyList());
                boolean allElementsSubmitted = !competencyElements.isEmpty() && competencyElements.stream()
                        .allMatch(elem -> subEvals.stream()
                                .anyMatch(e -> elem.elementId().equals(e.getElementId())
                                        && "SUBMITTED".equals(e.getConfirmStatusCode())));
                if (allElementsSubmitted) subSubmittedCount++;
            }
            boolean subordinateAllSubmitted = subTotal > 0 && subSubmittedCount == subTotal;

            model.addAttribute("subordinateAllSubmitted", subordinateAllSubmitted);
            model.addAttribute("subordinateTotal", subTotal);
            model.addAttribute("subordinateSubmittedCount", subSubmittedCount);
        }

        return "eval/final-grade/wizard";
    }

    @PostMapping("/submit")
    public String submitForm(@RequestParam Long mappingId,
                             @RequestParam Map<String, String> params,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {

        Long empId = Long.parseLong(userDetails.getUsername());
        
        EvaluatorMappingDTO submitMapping = mappingService.getMappingById(mappingId);
        Employee submitEvaluatee = employeeMapper.findById(submitMapping.evaluateeId()).orElse(null);
        Long submitDeptId = (submitEvaluatee != null) ? submitEvaluatee.getDeptId() : null;

        boolean isLeader = departmentMapper.countDepartmentsByLeaderId(submitMapping.evaluateeId()) > 0;
        String targetRoleCode = isLeader ? "LEADER" : "STAFF";

        if (!typeWeightService.isWeightSumValid(submitMapping.periodId(), submitDeptId, targetRoleCode)) {
            redirectAttributes.addFlashAttribute("errorMessage", "유형별 가중치 합계가 올바르지 않습니다.");
            return "redirect:/eval/final-grade/form?mappingId=" + mappingId;
        }

        // 부서장인 경우: 부서원(SUBORDINATE) 다면평가 전원 제출 완료 검증
        if (isLeader) {
            List<com.ees.eval.domain.EvaluatorMapping> allEvaluateeMappings = evaluatorMappingMapper
                    .findByEvaluateeId(submitMapping.periodId(), submitMapping.evaluateeId());
            List<Long> subMappingIds = allEvaluateeMappings.stream()
                    .filter(m -> "SUBORDINATE".equals(m.getRelationTypeCode()))
                    .map(com.ees.eval.domain.EvaluatorMapping::getMappingId)
                    .collect(Collectors.toList());

            if (!subMappingIds.isEmpty()) {
                List<Evaluation> subEvals = evaluationMapper.findByMappingIds(subMappingIds);
                Map<Long, List<Evaluation>> subEvalGroup = subEvals.stream()
                        .collect(Collectors.groupingBy(Evaluation::getMappingId));

                List<EvaluationElementDTO> multiElements = elementService
                        .getElementsWithFallback(submitMapping.periodId(), submitDeptId)
                        .stream()
                        .filter(e -> "MULTI_DIMENSIONAL".equals(e.elementTypeCode()))
                        .collect(Collectors.toList());

                for (Long subMappingId : subMappingIds) {
                    List<Evaluation> evals = subEvalGroup.getOrDefault(subMappingId, java.util.Collections.emptyList());
                    boolean allSubSubmitted = !multiElements.isEmpty() && multiElements.stream()
                            .allMatch(elem -> evals.stream()
                                    .anyMatch(e -> elem.elementId().equals(e.getElementId())
                                            && "SUBMITTED".equals(e.getConfirmStatusCode())));
                    if (!allSubSubmitted) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "부서원 다면평가가 모두 완료되지 않아 제출할 수 없습니다.");
                        return "redirect:/eval/final-grade/form?mappingId=" + mappingId;
                    }
                }
            }
        }

        // 평가 데이터 Upsert 처리
        try {
            evaluationService.upsertEvaluations(mappingId, params, empId);
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "잘못된 점수 형식입니다.");
            return "redirect:/eval/final-grade/form?mappingId=" + mappingId;
        }

        // 모든 항목 저장 완료 후 → 종합 점수 계산 및 final_grades_51에 확정 저장
        try {
            Integer totalScore = scoreCalculationService.calculateTotalScore(
                    submitMapping.periodId(), submitMapping.evaluateeId());
            if (totalScore != null) {
                finalGradeMapper.findByPeriodIdAndEmpId(
                        submitMapping.periodId(), submitMapping.evaluateeId())
                    .ifPresentOrElse(
                        existing -> {
                            existing.setTotalScore(totalScore);
                            existing.setUpdatedAt(java.time.LocalDateTime.now());
                            existing.setUpdatedBy(empId);
                            finalGradeMapper.update(existing);
                        },
                        () -> {
                            FinalGrade fg = FinalGrade.builder()
                                    .periodId(submitMapping.periodId())
                                    .empId(submitMapping.evaluateeId())
                                    .totalScore(totalScore)
                                    .finalGradeCode(null) // 실시간 재계산에서 부여됨
                                    .isDeleted("n")
                                    .version(0)
                                    .createdAt(java.time.LocalDateTime.now())
                                    .createdBy(empId)
                                    .build();
                            finalGradeMapper.insert(fg);
                        }
                    );
                log.info("[FinalGrade] 종합 점수 저장 완료 - empId={}, score={}",
                        submitMapping.evaluateeId(), totalScore);
                        
                // 해당 부서 전체의 상대평가 랭킹 및 등급 실시간 재계산
                if (submitDeptId != null) {
                    scoreCalculationService.calculateRelativeGradesForDepartment(
                            submitMapping.periodId(), submitDeptId);
                    log.info("[FinalGrade] 부서 단위 상대평가 실시간 랭킹 산정 완료 - deptId={}", submitDeptId);
                }
            }
        } catch (Exception e) {
            log.error("[FinalGrade] 종합 점수 계산 중 오류 - evaluateeId={}",
                    submitMapping.evaluateeId(), e);
        }

        redirectAttributes.addFlashAttribute("successMessage", "평가가 성공적으로 제출되었습니다.");
        return "redirect:/eval/final-grade?periodId=" + submitMapping.periodId() + (isLeader ? "&tab=leader" : "&tab=staff");
    }
}

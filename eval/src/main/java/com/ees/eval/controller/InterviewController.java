package com.ees.eval.controller;

import com.ees.eval.domain.EvaluationPeriod;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.dto.InterviewDTO;
import com.ees.eval.dto.InterviewTaskDTO;
import com.ees.eval.service.EmployeeService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluatorMappingService;
import com.ees.eval.service.InterviewService;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.dto.DepartmentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/eval/interview")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EXECUTIVE', 'USER')")
public class InterviewController {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final InterviewService interviewService;
    private final EmployeeService employeeService;
    private final DepartmentMapper departmentMapper;
    private final EvaluatorMappingMapper evaluatorMappingMapper;
    private final EmployeeMapper employeeMapper;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Long periodId,
                       @RequestParam(required = false) Long filterDeptId,
                       @RequestParam(required = false) String searchName,
                       @AuthenticationPrincipal UserDetails userDetails) {

        model.addAttribute("activeMenu", "interview-mgmt");
        Long empId = Long.parseLong(userDetails.getUsername());

        // 1. 차수 목록 조회 (PLANNED 제외)
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods().stream()
                .filter(p -> !"PLANNED".equals(p.statusCode()))
                .collect(Collectors.toList());
        model.addAttribute("periods", periods);

        EvaluationPeriodDTO selectedPeriod = null;
        boolean isAllPeriods = false;
        if (periodId != null) {
            if (periodId == 0L) {
                isAllPeriods = true;
            } else {
                selectedPeriod = periods.stream()
                        .filter(p -> p.periodId().equals(periodId))
                        .findFirst()
                        .orElse(null);
            }
        }
        if (periodId == null && !periods.isEmpty()) {
            selectedPeriod = periods.stream()
                    .filter(p -> "IN_PROGRESS".equals(p.statusCode()))
                    .findFirst()
                    .orElse(periods.get(0));
        }

        // 전체 부서 목록 조회
        List<DepartmentDTO> allDepartments = departmentMapper.findAll().stream()
                .map(d -> DepartmentDTO.builder()
                        .deptId(d.getDeptId())
                        .deptName(d.getDeptName())
                        .parentDeptId(d.getParentDeptId())
                        .build())
                .collect(Collectors.toList());

        List<DepartmentDTO> departments = new ArrayList<>(allDepartments);

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (selectedPeriod != null || isAllPeriods) {
            if (selectedPeriod != null) {
                model.addAttribute("selectedPeriod", selectedPeriod);
            }
            Long queryPeriodId = selectedPeriod != null ? selectedPeriod.periodId() : null;

            // 1. 내가 평가자로서 작성해야 할 목록 (부서장용)
            List<EvaluatorMappingDTO> teamTasks;
            List<EvaluatorMappingDTO> receivedTasks;
            if (isAdmin) {
                teamTasks = mappingService.getAllPerformanceTasks(queryPeriodId);
                receivedTasks = java.util.Collections.emptyList();
            } else {
                List<EvaluatorMappingDTO> myTasks = mappingService.getMyEvaluationTasks(queryPeriodId, empId);
                teamTasks = myTasks.stream()
                        .filter(m -> "MANAGER".equals(m.relationTypeCode()) || "EXECUTIVE".equals(m.relationTypeCode()))
                        .toList();

                // 2. 피평가자로서의 결과 목록
                receivedTasks = mappingService.getMyEvaluators(queryPeriodId, empId).stream()
                        .filter(m -> "MANAGER".equals(m.relationTypeCode()))
                        .toList();
            }

            // 권한 기반 부서 필터링: Admin이 아니라면 자신이 볼 수 있는 대상(teamTasks, receivedTasks, 본인 부서)의 부서만 드롭다운에 노출
            if (!isAdmin) {
                Set<String> allowedDeptNames = new java.util.HashSet<>();
                teamTasks.forEach(t -> {
                    if (t.deptName() != null) allowedDeptNames.add(t.deptName());
                });
                receivedTasks.forEach(r -> {
                    if (r.deptName() != null) allowedDeptNames.add(r.deptName());
                });
                List<com.ees.eval.domain.Employee> meList = employeeMapper.findByIds(List.of(empId));
                com.ees.eval.domain.Employee me = meList.isEmpty() ? null : meList.get(0);
                if (me != null && me.getDeptName() != null) {
                    allowedDeptNames.add(me.getDeptName());
                }

                // 계층 구조 유지를 위해 하위 부서의 상위 부서 ID도 함께 수집
                Set<Long> allowedParentIds = departments.stream()
                        .filter(d -> allowedDeptNames.contains(d.deptName()))
                        .map(DepartmentDTO::parentDeptId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

                departments = departments.stream()
                        .filter(d -> allowedDeptNames.contains(d.deptName()) || allowedParentIds.contains(d.deptId()))
                        .collect(Collectors.toList());
            }

            // 모든 관련 Mapping ID 수집 (N+1 방지)
            List<Long> allMappingIds = new ArrayList<>();
            teamTasks.forEach(m -> allMappingIds.add(m.mappingId()));
            receivedTasks.forEach(m -> allMappingIds.add(m.mappingId()));

            // 임원이 조회할 대상자들의 부서장 매핑 ID 찾기 및 상태 조회를 위해 ID 추가
            java.util.Map<Long, Long> evaluateeToManagerMappingId = new java.util.HashMap<>();
            List<Long> executiveTargetEvaluateeIds = teamTasks.stream()
                    .filter(m -> "EXECUTIVE".equals(m.relationTypeCode()))
                    .map(EvaluatorMappingDTO::evaluateeId)
                    .toList();

            if (!executiveTargetEvaluateeIds.isEmpty()) {
                List<com.ees.eval.domain.EvaluatorMapping> managerMappings = 
                        evaluatorMappingMapper.findByEvaluateeIds(queryPeriodId, executiveTargetEvaluateeIds);
                for (com.ees.eval.domain.EvaluatorMapping m : managerMappings) {
                    if ("MANAGER".equals(m.getRelationTypeCode())) {
                        evaluateeToManagerMappingId.put(m.getEvaluateeId(), m.getMappingId());
                        if (!allMappingIds.contains(m.getMappingId())) {
                            allMappingIds.add(m.getMappingId());
                        }
                    }
                }
            }

            // Batch 조회 실행
            java.util.Map<Long, InterviewDTO> interviewMap = interviewService.getInterviewsByMappingIds(allMappingIds);

            // 3. 팀 평가 목록 DTO 변환
            List<InterviewTaskDTO> tasks = teamTasks.stream().map(m -> {
                Long targetMappingId = m.mappingId();
                if ("EXECUTIVE".equals(m.relationTypeCode())) {
                    targetMappingId = evaluateeToManagerMappingId.getOrDefault(m.evaluateeId(), m.mappingId());
                }
                InterviewDTO interview = interviewMap.get(targetMappingId);
                String previewContent = getPreviewContent(interview);
                return InterviewTaskDTO.builder()
                        .mappingId(m.mappingId()) // UI 액션 링크 유지를 위해 본인 매핑 ID 사용
                        .empId(m.evaluateeId())
                        .evaluateeName(m.evaluateeName())
                        .deptName(m.deptName())
                        .titleName(m.titleName())
                        .relationTypeCode(m.relationTypeCode())
                        .statusCode(interview != null ? interview.statusCode() : "NOT_STARTED")
                        .contentSnippet(previewContent.length() > 50 ? previewContent.substring(0, 50) + "..." : previewContent.trim())
                        .evaluatorName(m.evaluatorName())
                        .periodName(m.periodName())
                        .build();
            }).collect(Collectors.toList());

            // 4. 본인 결과 목록 DTO 변환 (부서장급 이상 제외 로직 유지)
            java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities = userDetails.getAuthorities();
            boolean isHighRank = authorities.stream()
                    .map(a -> a.getAuthority().toUpperCase())
                    .anyMatch(auth -> auth.contains("EXECUTIVE") || auth.contains("ADMIN"));

            List<Long> evaluatorIds = receivedTasks.stream()
                    .map(EvaluatorMappingDTO::evaluatorId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();

            java.util.Map<Long, com.ees.eval.domain.Employee> evaluatorMap = new java.util.HashMap<>();
            if (!isHighRank && !evaluatorIds.isEmpty()) {
                employeeMapper.findByIds(evaluatorIds).forEach(emp -> evaluatorMap.put(emp.getEmpId(), emp));
            }

            List<InterviewTaskDTO> myResults = isHighRank ? java.util.Collections.emptyList() : receivedTasks.stream().map(m -> {
                InterviewDTO interview = interviewMap.get(m.mappingId());
                String previewContent = getPreviewContent(interview);
                com.ees.eval.domain.Employee evaluator = evaluatorMap.get(m.evaluatorId());
                return InterviewTaskDTO.builder()
                        .mappingId(m.mappingId())
                        .empId(m.evaluateeId())
                        .evaluateeName(m.evaluatorName())
                        .deptName(evaluator != null ? evaluator.getDeptName() : "")
                        .titleName(evaluator != null ? evaluator.getPositionName() : "")
                        .relationTypeCode(m.relationTypeCode())
                        .statusCode(interview != null ? interview.statusCode() : "NOT_STARTED")
                        .contentSnippet(previewContent.length() > 50 ? previewContent.substring(0, 50) + "..." : previewContent.trim())
                        .evaluatorName(m.evaluatorName())
                        .periodName(m.periodName())
                        .build();
            }).collect(Collectors.toList());

            // 5. 서버 사이드 부서 및 검색 필터 적용
            if (filterDeptId != null) {
                // 선택된 부서 및 모든 하위 부서의 ID 수집 (재귀적 탐색)
                Set<Long> targetDeptIds = new HashSet<>();
                targetDeptIds.add(filterDeptId);
                
                // 전체 부서 목록 로드 (중복 쿼리 제거 및 재사용)
                List<DepartmentDTO> allDepts = allDepartments;

                boolean added;
                do {
                    added = false;
                    for (DepartmentDTO dept : allDepts) {
                        if (dept.parentDeptId() != null && targetDeptIds.contains(dept.parentDeptId())) {
                            if (targetDeptIds.add(dept.deptId())) {
                                added = true;
                            }
                        }
                    }
                } while (added);

                Set<String> targetDeptNames = allDepts.stream()
                        .filter(d -> targetDeptIds.contains(d.deptId()))
                        .map(DepartmentDTO::deptName)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

                if (!targetDeptNames.isEmpty()) {
                    tasks = tasks.stream()
                            .filter(t -> t.deptName() != null && targetDeptNames.contains(t.deptName()))
                            .collect(Collectors.toList());
                    myResults = myResults.stream()
                            .filter(r -> r.deptName() != null && targetDeptNames.contains(r.deptName()))
                            .collect(Collectors.toList());
                }
            }

            if (searchName != null && !searchName.trim().isEmpty()) {
                final String query = searchName.trim().toLowerCase();
                tasks = tasks.stream()
                        .filter(t -> (t.evaluateeName() != null && t.evaluateeName().toLowerCase().contains(query)) ||
                                     (t.empId() != null && String.valueOf(t.empId()).contains(query)))
                        .collect(Collectors.toList());
                myResults = myResults.stream()
                        .filter(r -> (r.evaluateeName() != null && r.evaluateeName().toLowerCase().contains(query)) ||
                                     (r.empId() != null && String.valueOf(r.empId()).contains(query)))
                        .collect(Collectors.toList());
            }

            model.addAttribute("tasks", tasks);
            model.addAttribute("myResults", myResults);
        }
        
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("departments", departments);
        model.addAttribute("selectedDeptId", filterDeptId);
        model.addAttribute("searchName", searchName);

        return "eval/interview/list";
    }

    private String getPreviewContent(InterviewDTO interview) {
        if (interview == null) return "";
        return interview.content1() != null ? interview.content1() : "";
    }

    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId,
                          Model model,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes redirectAttributes) {

        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);
        Long empId = Long.parseLong(userDetails.getUsername());

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        // 권한 확인: 본인이 평가자이거나 피평가자 또는 관리자인지 확인 (null-safe)
        boolean isEvaluator = java.util.Objects.equals(mapping.evaluatorId(), empId);
        boolean isEvaluatee = java.util.Objects.equals(mapping.evaluateeId(), empId);

        log.info("Interview form request - mappingId: {}, empId: {}, isEvaluator: {}, isEvaluatee: {}, isAdmin: {}", 
                mappingId, empId, isEvaluator, isEvaluatee, isAdmin);

        if (!isEvaluator && !isEvaluatee && !isAdmin) {
            log.warn("Unauthorized access attempt to interview form - empId: {}, mappingId: {}", empId, mappingId);
            redirectAttributes.addFlashAttribute("errorMessage", "잘못된 접근입니다.");
            return "redirect:/eval/interview";
        }

        boolean isExecutive = "EXECUTIVE".equals(mapping.relationTypeCode());
        Long targetMappingId = mappingId;

        if (isExecutive && isEvaluator) {
            java.util.List<EvaluatorMappingDTO> evaluators = mappingService.getMyEvaluators(mapping.periodId(), mapping.evaluateeId());
            targetMappingId = evaluators.stream()
                    .filter(m -> "MANAGER".equals(m.relationTypeCode()))
                    .map(EvaluatorMappingDTO::mappingId)
                    .findFirst()
                    .orElse(mappingId);
            
            // 임원은 수정 불가 (읽기 전용)
            isEvaluator = false;
            model.addAttribute("isExecutiveReadOnly", true);
        } else if (isAdmin && !isEvaluator) {
            // 관리자이면서 실제 평가자가 아닌 경우 읽기 전용 처리
            model.addAttribute("isExecutiveReadOnly", true);
        } else {
            model.addAttribute("isExecutiveReadOnly", false);
        }

        // 1. 인터뷰 데이터 조회 (타겟 매핑 ID 기준, 없으면 기본값 생성)
        InterviewDTO interview = interviewService.getInterviewByMappingId(targetMappingId)
                .orElse(InterviewDTO.builder()
                        .mappingId(targetMappingId)
                        .content1("")
                        .content2("")
                        .content3("")
                        .content4("")
                        .statusCode("NOT_STARTED")
                        .build());
        
        // 2. 피평가자는 확정(COMPLETED)된 면담 기록만 조회 가능
        if (isEvaluatee && !"COMPLETED".equals(interview.statusCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "아직 확정된 면담 기록이 없습니다.");
            return "redirect:/eval/interview";
        }

        // 3. Record를 Map으로 변환하여 타임리프 렌더링 오류 방지
        java.util.Map<String, Object> mappingMap = new java.util.HashMap<>();
        mappingMap.put("mappingId", mapping.mappingId());
        mappingMap.put("evaluateeName", mapping.evaluateeName());
        mappingMap.put("relationTypeCode", mapping.relationTypeCode());
        EvaluationPeriodDTO period = periodService.getPeriodById(mapping.periodId());
        mappingMap.put("periodName", period != null ? period.periodName() : "");
        model.addAttribute("mapping", mappingMap);

        java.util.Map<String, Object> interviewMap = new java.util.HashMap<>();
        interviewMap.put("content1", interview.content1() != null ? interview.content1() : "");
        interviewMap.put("content2", interview.content2() != null ? interview.content2() : "");
        interviewMap.put("content3", interview.content3() != null ? interview.content3() : "");
        interviewMap.put("content4", interview.content4() != null ? interview.content4() : "");
        interviewMap.put("statusCode", interview.statusCode() != null ? interview.statusCode() : "NOT_STARTED");
        interviewMap.put("updatedAt", interview.updatedAt());
        model.addAttribute("interview", interviewMap);

        model.addAttribute("isEvaluator", isEvaluator);
        model.addAttribute("isEvaluatee", isEvaluatee);
        model.addAttribute("activeMenu", "interview-mgmt");

        return "eval/interview/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam Long mappingId,
                       @RequestParam(required = false) String content1,
                       @RequestParam(required = false) String content2,
                       @RequestParam(required = false) String content3,
                       @RequestParam(required = false) String content4,
                       @RequestParam String statusCode,
                       @AuthenticationPrincipal UserDetails userDetails,
                       RedirectAttributes redirectAttributes) {

        Long empId = Long.parseLong(userDetails.getUsername());
        
        // 권한 확인: 본인이 평가자인지 확인
        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);
        boolean isEvaluator = java.util.Objects.equals(mapping.evaluatorId(), empId);
        if (!isEvaluator) {
            log.warn("Unauthorized save attempt to interview - empId: {}, mappingId: {}", empId, mappingId);
            redirectAttributes.addFlashAttribute("errorMessage", "잘못된 접근입니다.");
            return "redirect:/eval/interview";
        }
        
        try {
            interviewService.saveInterview(mappingId, content1, content2, content3, content4, statusCode, empId);
            redirectAttributes.addFlashAttribute("successMessage", 
                    "COMPLETED".equals(statusCode) ? "면담 기록이 확정되었습니다." : "면담 기록이 임시저장되었습니다.");
        } catch (Exception e) {
            log.error("Error saving interview", e);
            redirectAttributes.addFlashAttribute("errorMessage", "저장 중 오류가 발생했습니다.");
        }

        return "redirect:/eval/interview/form?mappingId=" + mappingId;
    }
}

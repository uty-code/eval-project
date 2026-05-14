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

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Long periodId,
                       @AuthenticationPrincipal UserDetails userDetails) {

        model.addAttribute("activeMenu", "interview-mgmt");
        Long empId = Long.parseLong(userDetails.getUsername());

        // 1. 차수 목록 조회 (PLANNED 제외)
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods().stream()
                .filter(p -> !"PLANNED".equals(p.statusCode()))
                .collect(Collectors.toList());
        model.addAttribute("periods", periods);

        EvaluationPeriodDTO selectedPeriod = periodService.resolveSelectedPeriod(periodId, periods);

        if (selectedPeriod != null) {
            model.addAttribute("selectedPeriod", selectedPeriod);

            // 1. 내가 평가자로서 작성해야 할 목록 (부서장용)
            List<EvaluatorMappingDTO> myTasks = mappingService.getMyEvaluationTasks(selectedPeriod.periodId(), empId);
            List<EvaluatorMappingDTO> teamTasks = myTasks.stream()
                    .filter(m -> "MANAGER".equals(m.relationTypeCode()) || "EXECUTIVE".equals(m.relationTypeCode()))
                    .toList();

            // 2. 피평가자로서의 결과 목록
            List<EvaluatorMappingDTO> receivedTasks = mappingService.getMyEvaluators(selectedPeriod.periodId(), empId).stream()
                    .filter(m -> "MANAGER".equals(m.relationTypeCode()))
                    .toList();

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

            for (Long evId : executiveTargetEvaluateeIds) {
                mappingService.getMyEvaluators(selectedPeriod.periodId(), evId).stream()
                        .filter(e -> "MANAGER".equals(e.relationTypeCode()))
                        .findFirst()
                        .ifPresent(e -> {
                            evaluateeToManagerMappingId.put(evId, e.mappingId());
                            if (!allMappingIds.contains(e.mappingId())) {
                                allMappingIds.add(e.mappingId());
                            }
                        });
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
                String combinedContent = getCombinedContent(interview);
                return InterviewTaskDTO.builder()
                        .mappingId(m.mappingId()) // UI 액션 링크 유지를 위해 본인 매핑 ID 사용
                        .empId(m.evaluateeId())
                        .evaluateeName(m.evaluateeName())
                        .deptName(m.deptName())
                        .titleName(m.titleName())
                        .relationTypeCode(m.relationTypeCode())
                        .statusCode(interview != null ? interview.statusCode() : "NOT_STARTED")
                        .contentSnippet(combinedContent.length() > 50 ? combinedContent.substring(0, 50) + "..." : combinedContent.trim())
                        .build();
            }).collect(Collectors.toList());

            model.addAttribute("tasks", tasks);

            // 4. 본인 결과 목록 DTO 변환 (부서장급 이상 제외 로직 유지)
            java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities = userDetails.getAuthorities();
            boolean isHighRank = authorities.stream()
                    .map(a -> a.getAuthority().toUpperCase())
                    .anyMatch(auth -> auth.contains("EXECUTIVE") || auth.contains("ADMIN"));

            List<InterviewTaskDTO> myResults = isHighRank ? java.util.Collections.emptyList() : receivedTasks.stream().map(m -> {
                InterviewDTO interview = interviewMap.get(m.mappingId());
                String combinedContent = getCombinedContent(interview);
                com.ees.eval.dto.EmployeeDTO evaluator = employeeService.getEmployeeById(m.evaluatorId());
                return InterviewTaskDTO.builder()
                        .mappingId(m.mappingId())
                        .empId(m.evaluateeId())
                        .evaluateeName(m.evaluatorName())
                        .deptName(evaluator.deptName())
                        .titleName(evaluator.positionName())
                        .relationTypeCode(m.relationTypeCode())
                        .statusCode(interview != null ? interview.statusCode() : "NOT_STARTED")
                        .contentSnippet(combinedContent.length() > 50 ? combinedContent.substring(0, 50) + "..." : combinedContent.trim())
                        .build();
            }).collect(Collectors.toList());

            model.addAttribute("myResults", myResults);
        }

        return "eval/interview/list";
    }

    private String getCombinedContent(InterviewDTO interview) {
        if (interview == null) return "";
        return (interview.content1() != null ? interview.content1() : "") + " " +
               (interview.content2() != null ? interview.content2() : "") + " " +
               (interview.content3() != null ? interview.content3() : "") + " " +
               (interview.content4() != null ? interview.content4() : "");
    }

    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId,
                          Model model,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes redirectAttributes) {

        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);
        Long empId = Long.parseLong(userDetails.getUsername());

        // 권한 확인: 본인이 평가자이거나 피평가자인지 확인 (null-safe)
        boolean isEvaluator = java.util.Objects.equals(mapping.evaluatorId(), empId);
        boolean isEvaluatee = java.util.Objects.equals(mapping.evaluateeId(), empId);

        log.info("Interview form request - mappingId: {}, empId: {}, isEvaluator: {}, isEvaluatee: {}", 
                mappingId, empId, isEvaluator, isEvaluatee);

        if (!isEvaluator && !isEvaluatee) {
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

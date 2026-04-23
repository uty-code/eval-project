package com.ees.eval.controller;

import com.ees.eval.domain.Evaluation;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluatorMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 성과/업무 평가 컨트롤러
 * 로그인한 사용자가 본인에게 할당된 평가 대상자(피평가자) 목록을 조회하고 평가를 수행하는 화면을 담당합니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/performance")
@RequiredArgsConstructor
public class PerformanceEvaluationController {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationMapper evaluationMapper;
    private final EvaluatorMappingMapper evaluatorMappingMapper;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Long periodId,
                       @AuthenticationPrincipal UserDetails userDetails) {

        Long empId = Long.parseLong(userDetails.getUsername());

        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        model.addAttribute("periods", periods);

        EvaluationPeriodDTO selectedPeriod = null;
        if (periodId != null) {
            selectedPeriod = periodService.getPeriodById(periodId);
        } else if (!periods.isEmpty()) {
            selectedPeriod = periods.stream()
                .filter(p -> "ACTIVE".equals(p.statusCode()))
                .findFirst()
                .orElse(periods.get(0));
        }

        if (selectedPeriod != null) {
            model.addAttribute("selectedPeriod", selectedPeriod);

            List<EvaluatorMappingDTO> myTasks = mappingService.getMyEvaluationTasks(selectedPeriod.periodId(), empId);

            EvaluatorMappingDTO selfTask = myTasks.stream()
                .filter(m -> "SELF".equals(m.relationTypeCode()))
                .findFirst()
                .orElse(null);

            List<EvaluatorMappingDTO> teamTasks = myTasks.stream()
                .filter(m -> "MANAGER".equals(m.relationTypeCode()) || "EXECUTIVE".equals(m.relationTypeCode()))
                .toList();

            model.addAttribute("selfTask", selfTask);
            model.addAttribute("tasks", teamTasks);

            // 자가평가 제출 여부 확인 (하나라도 SUBMITTED 상태인 레코드가 있으면 완료)
            boolean selfSubmitted = false;
            if (selfTask != null) {
                selfSubmitted = evaluationMapper.findByMappingId(selfTask.mappingId())
                    .stream()
                    .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
            }
            model.addAttribute("selfSubmitted", selfSubmitted);

            // 팀원 평가별 제출 여부 Map (mappingId → 제출완료 여부)
            java.util.Map<Long, Boolean> teamSubmittedMap = new java.util.HashMap<>();
            for (EvaluatorMappingDTO task : teamTasks) {
                boolean submitted = evaluationMapper.findByMappingId(task.mappingId())
                    .stream()
                    .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
                teamSubmittedMap.put(task.mappingId(), submitted);
            }
            model.addAttribute("teamSubmittedMap", teamSubmittedMap);
        }

        return "eval/performance/list";
    }

    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId, Model model,
                          @AuthenticationPrincipal UserDetails userDetails) {

        // 매핑 정보 조회 (피평가자 정보, 차수 정보 포함)
        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);
        model.addAttribute("mapping", mapping);

        // 해당 차수의 평가요소 목록 조회 (전사 공통 기준)
        List<EvaluationElementDTO> allElements = elementService.getElementsByPeriodId(mapping.periodId(), null);

        // 성과평가 페이지이므로 PERFORMANCE 타입만 필터링
        List<EvaluationElementDTO> elements = allElements.stream()
            .filter(e -> "PERFORMANCE".equals(e.elementTypeCode()))
            .toList();

        model.addAttribute("elements", elements);
        model.addAttribute("mappingId", mappingId);

        // 기존에 제출된 평가 내용 조회 → elementId 기준 Map으로 변환하여 템플릿에 전달
        java.util.Map<Long, com.ees.eval.domain.Evaluation> savedMap = evaluationMapper
            .findByMappingId(mappingId)
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                com.ees.eval.domain.Evaluation::getElementId,
                e -> e
            ));
        model.addAttribute("savedMap", savedMap);

        // 제출 여부 판단: 하나라도 SUBMITTED 상태면 submitted=true
        boolean submitted = savedMap.values().stream()
            .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
        model.addAttribute("submitted", submitted);

        // MANAGER/EXECUTIVE 평가인 경우: 피평가자의 자가평가 내용을 참고용으로 조회
        if ("MANAGER".equals(mapping.relationTypeCode()) || "EXECUTIVE".equals(mapping.relationTypeCode())) {
            // 피평가자(evaluateeId)의 SELF 매핑 조회
            java.util.Map<Long, String> selfEvalMap = evaluatorMappingMapper
                .findByEvaluateeId(mapping.periodId(), mapping.evaluateeId())
                .stream()
                .filter(m -> "SELF".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                .findFirst()
                .map(selfMapping -> evaluationMapper.findByMappingId(selfMapping.getMappingId())
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                        com.ees.eval.domain.Evaluation::getElementId,
                        e -> e.getComments() != null ? e.getComments() : ""
                    )))
                .orElse(java.util.Collections.emptyMap());
            model.addAttribute("selfEvalMap", selfEvalMap);
        }

        return "eval/performance/form";
    }

    /**
     * 평가 폼 제출 처리 - 각 평가요소에 대한 서술형 코멘트를 저장합니다.
     * 이미 저장된 데이터가 있으면 UPDATE, 없으면 INSERT(Upsert) 방식으로 처리합니다.
     */
    @PostMapping("/submit")
    public String submitForm(@RequestParam Long mappingId,
                             @RequestParam java.util.Map<String, String> params,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {

        Long empId = Long.parseLong(userDetails.getUsername());
        log.info("[평가제출] empId={}, mappingId={}", empId, mappingId);

        // elementId 추출 및 데이터 그룹화
        java.util.Set<Long> elementIds = new java.util.HashSet<>();
        params.keySet().forEach(key -> {
            if (key.startsWith("comment_") || key.startsWith("score_")) {
                try {
                    elementIds.add(Long.parseLong(key.substring(key.indexOf("_") + 1)));
                } catch (Exception ignore) {}
            }
        });

        for (Long elementId : elementIds) {
            String comment = params.get("comment_" + elementId);
            String scoreStr = params.get("score_" + elementId);

            java.math.BigDecimal score = null;
            if (scoreStr != null && !scoreStr.trim().isEmpty()) {
                try {
                    score = new java.math.BigDecimal(scoreStr.trim());
                } catch (Exception e) {
                    log.warn("[평가제출] 점수 파싱 실패: elementId={}, scoreStr={}", elementId, scoreStr);
                }
            }

            final java.math.BigDecimal finalScore = score;
            final String finalComment = (comment != null) ? comment.trim() : "";

            // 이미 저장된 레코드가 있으면 UPDATE, 없으면 INSERT
            evaluationMapper.findByMappingIdAndElementId(mappingId, elementId)
                .ifPresentOrElse(
                    existing -> {
                        existing.setComments(finalComment);
                        existing.setScore(finalScore);
                        existing.setConfirmStatusCode("SUBMITTED");
                        existing.preUpdate();
                        evaluationMapper.update(existing);
                    },
                    () -> {
                        Evaluation eval = Evaluation.builder()
                            .mappingId(mappingId)
                            .elementId(elementId)
                            .comments(finalComment)
                            .score(finalScore)
                            .confirmStatusCode("SUBMITTED")
                            .build();
                        eval.prePersist();
                        eval.setCreatedBy(empId);
                        eval.setUpdatedBy(empId);
                        evaluationMapper.insert(eval);
                    }
                );
        }

        redirectAttributes.addFlashAttribute("successMessage", "평가가 성공적으로 제출되었습니다.");
        return "redirect:/eval/performance/form?mappingId=" + mappingId;
    }
}

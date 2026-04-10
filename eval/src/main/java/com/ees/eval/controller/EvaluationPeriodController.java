package com.ees.eval.controller;

import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.service.EvaluationPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

/**
 * 평가 차수(EvaluationPeriod) 관리 화면을 위한 컨트롤러입니다.
 * 차수 목록 조회, 생성, 수정, 상태 전이 등의 화면 진입점과 폼 처리를 담당합니다.
 *
 * <p>접근 권한: ADMIN 역할을 가진 사용자만 접근 가능합니다.</p>
 */
@Slf4j
@Controller
@RequestMapping("/eval/periods")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EvaluationPeriodController {

    private final EvaluationPeriodService periodService;

    /**
     * 평가 차수 목록 화면을 반환합니다.
     * 전체 차수 목록을 조회하여 모델에 담아 전달합니다.
     *
     * @param model Thymeleaf 모델 객체
     * @return eval/periods/list.html 템플릿 경로
     */
    @GetMapping
    public String listPeriods(Model model) {
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        model.addAttribute("periods", periods);
        log.info("평가 차수 목록 조회 - 총 {}건", periods.size());
        return "eval/periods/list";
    }

    /**
     * 평가 차수 신규 생성 폼 화면을 반환합니다.
     * 빈 DTO를 모델에 담아 전달합니다.
     *
     * @param model Thymeleaf 모델 객체
     * @return eval/periods/form.html 템플릿 경로
     */
    @GetMapping("/new")
    public String createForm(Model model) {
        // 기본값 설정: 현재 연도, 오늘 날짜
        EvaluationPeriodDTO emptyDto = EvaluationPeriodDTO.builder()
                .periodYear(LocalDate.now().getYear())
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .build();
        model.addAttribute("period", emptyDto);
        model.addAttribute("isNew", true);
        return "eval/periods/form";
    }

    /**
     * 평가 차수 수정 폼 화면을 반환합니다.
     * 기존 차수 정보를 조회하여 모델에 담아 전달합니다.
     *
     * @param periodId 수정할 차수 식별자
     * @param model Thymeleaf 모델 객체
     * @return eval/periods/form.html 템플릿 경로
     */
    @GetMapping("/{periodId}/edit")
    public String editForm(@PathVariable Long periodId, Model model) {
        EvaluationPeriodDTO period = periodService.getPeriodById(periodId);
        model.addAttribute("period", period);
        model.addAttribute("isNew", false);
        return "eval/periods/form";
    }

    /**
     * 평가 차수를 신규 생성합니다.
     * 폼으로부터 전달된 데이터를 기반으로 차수를 생성하고 목록으로 리다이렉트합니다.
     *
     * @param periodYear 평가 연도
     * @param periodName 차수 명칭
     * @param startDate 시작일
     * @param endDate 종료일
     * @param redirectAttributes 성공 메시지 전달용 플래시 속성
     * @return 차수 목록으로 리다이렉트
     */
    @PostMapping
    public String createPeriod(@ModelAttribute("period") @Valid EvaluationPeriodDTO dto,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        // 1. 기본 검증(JSR-303) 체크
        if (result.hasErrors()) {
            model.addAttribute("isNew", true);
            return "eval/periods/form";
        }

        // 2. 비즈니스 유효성 검증 (시작일/종료일 상호 체크)
        if (dto.endDate().isBefore(dto.startDate())) {
            model.addAttribute("errorMessage", "종료일은 시작일보다 이후여야 합니다.");
            model.addAttribute("isNew", true);
            return "eval/periods/form";
        }

        EvaluationPeriodDTO created = periodService.createPeriod(dto);
        log.info("평가 차수 생성 완료 - periodId: {}, name: {}", created.periodId(), created.periodName());

        redirectAttributes.addFlashAttribute("successMessage",
                "'" + created.periodName() + "' 차수가 성공적으로 생성되었습니다.");
        return "redirect:/eval/periods";
    }

    /**
     * 기존 평가 차수 정보를 수정합니다.
     * 낙관적 락(version)을 통해 동시 수정 충돌을 감지합니다.
     *
     * @param periodId 수정할 차수 식별자
     * @param periodYear 평가 연도
     * @param periodName 차수 명칭
     * @param startDate 시작일
     * @param endDate 종료일
     * @param version 낙관적 락 현재 버전
     * @param redirectAttributes 성공/에러 메시지 전달용 플래시 속성
     * @return 차수 목록으로 리다이렉트
     */
    @PostMapping("/{periodId}")
    public String updatePeriod(@PathVariable Long periodId,
                               @ModelAttribute("period") @Valid EvaluationPeriodDTO dto,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        // 1. 기본 검증(JSR-303) 체크
        if (result.hasErrors()) {
            model.addAttribute("isNew", false);
            return "eval/periods/form";
        }

        // 2. 비즈니스 유효성 검증
        if (dto.endDate().isBefore(dto.startDate())) {
            model.addAttribute("errorMessage", "종료일은 시작일보다 이후여야 합니다.");
            model.addAttribute("isNew", false);
            return "eval/periods/form";
        }

        // 서비스 호출을 위해 DTO에 ID와 버전이 확실히 포함되도록 빌더 사용 (ID는 경로변수 우선)
        EvaluationPeriodDTO updateDto = EvaluationPeriodDTO.builder()
                .periodId(periodId)
                .periodYear(dto.periodYear())
                .periodName(dto.periodName())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .version(dto.version())
                .build();

        periodService.updatePeriod(updateDto);
        log.info("평가 차수 수정 완료 - periodId: {}", periodId);

        redirectAttributes.addFlashAttribute("successMessage",
                "'" + dto.periodName() + "' 차수 정보가 수정되었습니다.");
        return "redirect:/eval/periods";
    }

    /**
     * 평가 차수의 상태를 전이합니다.
     * 전이 규칙: PLANNED → IN_PROGRESS → COMPLETED → CLOSED
     *
     * @param periodId 대상 차수 식별자
     * @param newStatus 전이할 새 상태 코드
     * @param redirectAttributes 성공/에러 메시지 전달용 플래시 속성
     * @return 차수 목록으로 리다이렉트
     */
    @PostMapping("/{periodId}/transition")
    public String transitionStatus(@PathVariable Long periodId,
                                    @RequestParam String newStatus,
                                    RedirectAttributes redirectAttributes) {
        EvaluationPeriodDTO updated = periodService.transitionStatus(periodId, newStatus);
        log.info("평가 차수 상태 전이 완료 - periodId: {}, newStatus: {}", periodId, updated.statusCode());

        // 상태별 한글 메시지 생성
        String statusName = switch (updated.statusCode()) {
            case "IN_PROGRESS" -> "진행 중";
            case "COMPLETED" -> "완료";
            case "CLOSED" -> "마감";
            default -> updated.statusCode();
        };

        redirectAttributes.addFlashAttribute("successMessage",
                "'" + updated.periodName() + "' 차수 상태가 '" + statusName + "'(으)로 변경되었습니다.");
        return "redirect:/eval/periods";
    }

    /**
     * 평가 차수를 논리적으로 삭제합니다.
     *
     * @param periodId 삭제할 차수 식별자
     * @param redirectAttributes 성공 메시지 전달용 플래시 속성
     * @return 차수 목록으로 리다이렉트
     */
    @PostMapping("/{periodId}/delete")
    public String deletePeriod(@PathVariable Long periodId,
                               RedirectAttributes redirectAttributes) {
        periodService.deletePeriod(periodId);
        log.info("평가 차수 삭제 완료 - periodId: {}", periodId);

        redirectAttributes.addFlashAttribute("successMessage", "차수가 삭제되었습니다.");
        return "redirect:/eval/periods";
    }
}

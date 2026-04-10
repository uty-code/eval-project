package com.ees.eval.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 애플리케이션 전역에서 발생하는 예외를 통합 처리하는 핸들러입니다.
 * 컨트롤러 계층에서 발생한 예외를 잡아 사용자에게 적절한 에러 메시지를 전달합니다.
 *
 * <p>
 * 주요 처리 대상:
 * </p>
 * <ul>
 * <li>{@link EesOptimisticLockException} - 낙관적 락 충돌 (409 Conflict)</li>
 * <li>{@link IllegalStateException} - 비즈니스 규칙 위반 (400 Bad Request)</li>
 * <li>{@link IllegalArgumentException} - 잘못된 파라미터 (400 Bad Request)</li>
 * <li>{@link Exception} - 기타 예상치 못한 오류 (500 Internal Server Error)</li>
 * </ul>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 낙관적 락 충돌 예외를 처리합니다.
     * 동시에 같은 데이터를 수정하려 할 때 발생하며, 사용자에게 재시도를 안내합니다.
     *
     * @param ex                 발생한 예외 객체
     * @param redirectAttributes 리다이렉트 시 전달할 플래시 속성
     * @return 이전 페이지로 리다이렉트
     */
    @ExceptionHandler(EesOptimisticLockException.class)
    public String handleOptimisticLockException(EesOptimisticLockException ex,
            RedirectAttributes redirectAttributes) {
        log.warn("낙관적 락 충돌 발생: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "다른 관리자가 동일한 데이터를 수정 중입니다. 페이지를 새로고침하고 다시 시도해 주세요.");
        return "redirect:/eval/periods";
    }

    /**
     * 비즈니스 규칙 위반 예외를 처리합니다.
     * 유효하지 않은 상태 전이 등 도메인 로직 위반 시 발생합니다.
     *
     * @param ex                 발생한 예외 객체
     * @param redirectAttributes 리다이렉트 시 전달할 플래시 속성
     * @return 이전 페이지로 리다이렉트
     */
    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalStateException(IllegalStateException ex,
            RedirectAttributes redirectAttributes) {
        log.warn("비즈니스 규칙 위반: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/eval/periods";
    }

    /**
     * DTO 유효성 검사 실패 시 발생하는 예외를 처리합니다.
     * (BindingResult가 명시되지 않은 경우나 기타 API 호출 시 발생)
     *
     * @param ex    발생한 예외 객체
     * @param model Thymeleaf 모델 객체
     * @return 에러 안내 페이지
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidationException(MethodArgumentNotValidException ex, Model model) {
        String firstErrorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("입력값 유효성 검사 실패: {}", firstErrorMessage);

        model.addAttribute("errorMessage", firstErrorMessage);
        model.addAttribute("statusCode", HttpStatus.BAD_REQUEST.value());
        return "error/custom-error";
    }

    /**
     * 잘못된 인자 예외를 처리합니다.
     * 존재하지 않는 리소스 조회 시 발생합니다.
     *
     * @param ex    발생한 예외 객체
     * @param model Thymeleaf 모델 객체
     * @return 에러 안내 페이지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException ex, Model model) {
        log.warn("잘못된 요청 파라미터: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("statusCode", HttpStatus.BAD_REQUEST.value());
        return "error/custom-error";
    }

    /**
     * 예상하지 못한 모든 예외를 최종적으로 처리합니다.
     * 개발 로그에 상세 스택 트레이스를 기록하고, 사용자에게는 일반적인 안내 메시지를 보여줍니다.
     *
     * @param ex    발생한 예외 객체
     * @param model Thymeleaf 모델 객체
     * @return 에러 안내 페이지
     */
    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception ex, Model model) {
        log.error("예기치 않은 서버 오류 발생: ", ex);
        model.addAttribute("errorMessage", "시스템에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        model.addAttribute("statusCode", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return "error/custom-error";
    }
}

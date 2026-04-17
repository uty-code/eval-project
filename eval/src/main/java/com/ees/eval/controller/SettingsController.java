package com.ees.eval.controller;

import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 내 설정(내 프로필, 연락처 수정, 비밀번호 변경) 요청을 처리하는 컨트롤러입니다.
 * 모든 인증된 사용자가 접근 가능합니다.
 */
@Slf4j
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final EmployeeService employeeService;

    /** 설정 메인 → 내 프로필로 리다이렉트 */
    @GetMapping
    public String settingsIndex() {
        return "redirect:/settings/profile";
    }

    /** 내 프로필 화면 */
    @GetMapping("/profile")
    public String profileForm(Authentication authentication, Model model) {
        try {
            Long empId = Long.parseLong(authentication.getName());
            EmployeeDTO employee = employeeService.getEmployeeById(empId);
            model.addAttribute("employee", employee);
        } catch (Exception e) {
            log.error("프로필 조회 실패: {}", e.getMessage());
        }
        return "settings/profile";
    }

    /** 이메일·전화번호 수정 */
    @PostMapping("/profile/contact")
    public String updateContact(
            @RequestParam("email") String email,
            @RequestParam(value = "phone", required = false) String phone,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            Long empId = Long.parseLong(authentication.getName());
            employeeService.updateContactInfo(empId, email, phone != null ? phone : "");
            redirectAttributes.addFlashAttribute("successMessage", "연락처가 성공적으로 수정되었습니다.");
        } catch (Exception e) {
            log.error("연락처 수정 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "연락처 수정 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/settings/profile";
    }

    /** 비밀번호 변경 */
    @PostMapping("/profile/password")
    public String changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("pwErrorMessage", "새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
                return "redirect:/settings/profile";
            }
            if (newPassword.length() < 4) {
                redirectAttributes.addFlashAttribute("pwErrorMessage", "새 비밀번호는 최소 4자 이상이어야 합니다.");
                return "redirect:/settings/profile";
            }
            Long empId = Long.parseLong(authentication.getName());
            employeeService.changePassword(empId, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("pwSuccessMessage", "비밀번호가 성공적으로 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            log.warn("비밀번호 변경 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("pwErrorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("비밀번호 변경 오류: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("pwErrorMessage", "비밀번호 변경 중 오류가 발생했습니다.");
        }
        return "redirect:/settings/profile";
    }
}

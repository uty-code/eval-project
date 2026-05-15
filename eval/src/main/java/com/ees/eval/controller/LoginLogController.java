package com.ees.eval.controller;

import com.ees.eval.domain.LoginLog;
import com.ees.eval.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 로그인 이력(Audit Log) 조회 컨트롤러입니다.
 * 관리자(ROLE_ADMIN)만 접근 가능합니다.
 */
@Slf4j
@Controller
@RequestMapping("/admin/login-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LoginLogController {

    private final LoginLogService loginLogService;

    /** 전체 로그인 이력 목록 */
    @GetMapping
    public String list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String keyword,
            Model model) {
        com.ees.eval.dto.PageResponseDTO<LoginLog> pageResponse = loginLogService.findAll(page, size, keyword);
        model.addAttribute("pageResponse", pageResponse);
        model.addAttribute("logs", pageResponse.getContent());
        model.addAttribute("keyword", keyword);
        return "admin/login-logs";
    }

    /** 특정 사원의 로그인 이력 */
    @GetMapping("/emp/{empId}")
    public String listByEmp(
            @PathVariable Long empId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String keyword,
            Model model) {
        com.ees.eval.dto.PageResponseDTO<LoginLog> pageResponse = loginLogService.findByEmpId(empId, page, size, keyword);
        model.addAttribute("pageResponse", pageResponse);
        model.addAttribute("logs", pageResponse.getContent());
        model.addAttribute("empId", empId);
        model.addAttribute("keyword", keyword);
        return "admin/login-logs";
    }
}

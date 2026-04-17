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
    public String list(Model model) {
        List<LoginLog> logs = loginLogService.findAll();
        model.addAttribute("logs", logs);
        return "admin/login-logs";
    }

    /** 특정 사원의 로그인 이력 */
    @GetMapping("/emp/{empId}")
    public String listByEmp(@PathVariable Long empId, Model model) {
        List<LoginLog> logs = loginLogService.findByEmpId(empId);
        model.addAttribute("logs", logs);
        model.addAttribute("empId", empId);
        return "admin/login-logs";
    }
}

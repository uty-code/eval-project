package com.ees.eval.controller;

import com.ees.eval.domain.ApiLog;
import com.ees.eval.service.ApiLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * API 호출 이력 로그 조회 컨트롤러입니다.
 * 관리자(ROLE_ADMIN)만 접근 가능합니다.
 */
@Slf4j
@Controller
@RequestMapping("/admin/api-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiLogController {

    private final ApiLogService apiLogService;

    /**
     * 다중 검색 필터를 적용하여 API 호출 이력 목록을 조회합니다.
     */
    @GetMapping
    public String list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String endDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String empId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String targetId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String ipAddress,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String traceId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String resultCode,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String httpMethod,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size,
            Model model) {
            
        // 날짜가 없으면 오늘 기준으로 설정
        if (startDate == null || startDate.isEmpty()) {
            startDate = java.time.LocalDate.now().toString();
        }
        if (endDate == null || endDate.isEmpty()) {
            endDate = java.time.LocalDate.now().toString();
        }

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        params.put("empId", empId);
        params.put("targetId", targetId);
        params.put("ipAddress", ipAddress);
        params.put("traceId", traceId);
        params.put("resultCode", resultCode);
        params.put("httpMethod", httpMethod);

        com.ees.eval.dto.PageResponseDTO<ApiLog> pageResponse = apiLogService.searchLogs(params, page, size);
                
        model.addAttribute("pageResponse", pageResponse);
        model.addAttribute("logs", pageResponse.getContent());
        model.addAttribute("params", params);
        
        return "admin/api-logs";
    }

    /**
     * Trace ID를 기반으로 전체 실행 흐름 타임라인을 비동기로 조회합니다.
     */
    @org.springframework.web.bind.annotation.ResponseBody
    @GetMapping("/api/trace/{traceId}")
    public org.springframework.http.ResponseEntity<List<ApiLog>> getTraceTimeline(@org.springframework.web.bind.annotation.PathVariable String traceId) {
        // Trace ID로 모든 로그를 시간순(오름차순)으로 가져옵니다. 
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("startDate", "2000-01-01");
        params.put("endDate", "2099-12-31");
        params.put("traceId", traceId);
        
        com.ees.eval.dto.PageResponseDTO<ApiLog> pageResponse = apiLogService.searchLogs(params, 1, 1000);
        List<ApiLog> timeline = pageResponse.getContent();
                
        // UI에서 보기 좋게 생성일시 오름차순(과거->현재) 정렬 (검색은 내림차순이므로 뒤집음)
        java.util.Collections.reverse(timeline);
        
        return org.springframework.http.ResponseEntity.ok(timeline);
    }
}

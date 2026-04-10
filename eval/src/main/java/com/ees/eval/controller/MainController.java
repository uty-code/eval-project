package com.ees.eval.controller;

import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 메인 대시보드 및 공통 화면 접근을 담당하는 컨트롤러입니다.
 * 시스템 현황 통계 정보를 화면에 제공합니다.
 */
@Controller
@RequiredArgsConstructor
public class MainController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;

    /**
     * 메인 대시보드 화면을 반환합니다.
     * 사원 수, 부서 수 등 기초 통계를 모델에 담아 전달합니다.
     *
     * @param model Thymeleaf 모델 객체
     * @return dashboard.html 템플릿 경로
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        // 1. 기초 통계 데이터
        model.addAttribute("employeeCount", employeeService.getAllEmployees().size());
        model.addAttribute("departmentCount", departmentService.getAllDepartments().size());
        model.addAttribute("activePeriodCount", 1); // 진행 중인 평가 차수 (Mock)
        
        // 2. 시각화용 데이터 (Notion 요구사항 기반 Mock 데이터)
        // 등급 분포
        model.addAttribute("gradeStats", Map.of(
            "S", 5, "A", 15, "B", 45, "C", 25, "D", 10
        ));
        
        // 부서별 평균/완료율 (예시)
        model.addAttribute("deptStats", Map.of(
            "개발팀", 92, "인사팀", 85, "영업팀", 78, "고객지원팀", 88
        ));

        model.addAttribute("welcomeMessage", "사원 평가 시스템(EES) 관리자 페이지에 오신 것을 환영합니다.");
        
        return "dashboard";
    }
}

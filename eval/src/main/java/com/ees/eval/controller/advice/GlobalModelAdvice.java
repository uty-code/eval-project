package com.ees.eval.controller.advice;

import com.ees.eval.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 컨트롤러에서 공통으로 사용할 모델 데이터를 관리하는 어드바이스 클래스입니다.
 * 사이드바의 승인 대기 배지 숫자 등을 전역적으로 주입합니다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final EmployeeService employeeService;

    /**
     * 모든 요청마다 승인 대기 사원 수를 "pendingCount"라는 이름으로 모델에 추가합니다.
     * 
     * @return 승인 대기 사원 수
     */
    @ModelAttribute("pendingCount")
    public long getPendingCount() {
        return employeeService.countPendingEmployees();
    }
}

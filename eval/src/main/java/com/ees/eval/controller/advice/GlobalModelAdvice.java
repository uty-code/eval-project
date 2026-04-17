package com.ees.eval.controller.advice;

import com.ees.eval.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

/**
 * 모든 컨트롤러에서 공통으로 사용할 모델 데이터를 관리하는 어드바이스 클래스입니다.
 * 사이드바의 승인 대기 배지 숫자 및 공통 통계 데이터를 전역적으로 주입합니다.
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

    /**
     * 모든 요청마다 계정 잠금 사원 수를 "lockedCount"라는 이름으로 모델에 추가합니다.
     */
    @ModelAttribute("lockedCount")
    public long getLockedCount() {
        return employeeService.countLockedEmployees();
    }

    /**
     * 모든 요청마다 재직 중인 사원 수를 "activeCount"라는 이름으로 모델에 추가합니다.
     */
    @ModelAttribute("activeCount")
    public long getActiveCount() {
        return employeeService.countActiveEmployees();
    }

    /**
     * 모든 요청마다 전체 사원 수(신청 대기 제외)를 "totalEmployeeCount"라는 이름으로 모델에 추가합니다.
     */
    @ModelAttribute("totalEmployeeCount")
    public long getTotalEmployeeCount() {
        // searchEmployeesPage(null, null, null, 1, 1).totalCount() 와 동일한 효과를 내기 위해
        // 서비스에 별도 카운트 메서드가 있다면 그것을 쓰는 것이 좋으나 현재는 search를 활용
        return employeeService.searchEmployeesPage(null, null, null, 1, 1).totalCount();
    }

    /**
     * 올해 입사자 수를 "thisYearHired"라는 이름으로 모델에 추가합니다.
     */
    @ModelAttribute("thisYearHired")
    public long getThisYearHired() {
        return employeeService.countThisYearHired();
    }
}

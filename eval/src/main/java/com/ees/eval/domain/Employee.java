package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 사원(employees) 테이블에 대응하는 핵심 도메인 엔티티 클래스입니다.
 * BaseEntity를 상속하여 감사(Audit) 필드, 소프트 삭제, 낙관적 락 기능을 제공받습니다.
 * 직급(positions)과 부서(departments)에 대한 외래키를 보유합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Employee extends BaseEntity {

    /** 사원 고유 식별자 (PK) */
    private Long empId;

    /** 소속 부서 식별자 (FK -> departments) */
    private Long deptId;

    /** 직급 식별자 (FK -> positions) */
    private Long positionId;

    /** BCrypt로 암호화된 비밀번호 */
    private String password;

    /** 사원 성명 */
    private String name;

    /** 이메일 주소 */
    private String email;

    /** 핸드폰 번호 */
    private String phone;

    /** 재직 상태 (예: EMPLOYED, RETIRED) */
    private String statusCode;

    /** 로그인 실패 횟수 (5회 오류 시 잠금) */
    private Integer loginFailCnt;

    /** 입사일 */
    private LocalDate hireDate;

    /** JOIN 조회 전용 - 부서 명칭 (departments 테이블에서 LEFT JOIN) */
    private String deptName;

    /** JOIN 조회 전용 - 직급 명칭 (positions 테이블에서 LEFT JOIN) */
    private String positionName;

    /**
     * 사원 엔티티를 생성하는 빌더 메서드입니다.
     *
     * @param empId        사원 ID
     * @param deptId       부서 ID
     * @param positionId   직급 ID
     * @param password     암호화된 비밀번호
     * @param name         사원 이름
     * @param email        이메일
     * @param statusCode   재직 상태
     * @param loginFailCnt 로그인 실패 횟수
     * @param hireDate     입사일
     */
    @Builder
    public Employee(Long empId, Long deptId, Long positionId,
            String password, String name, String email, String phone, String statusCode, Integer loginFailCnt,
            LocalDate hireDate) {
        this.empId = empId;
        this.deptId = deptId;
        this.positionId = positionId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.statusCode = statusCode;
        this.loginFailCnt = loginFailCnt;
        this.hireDate = hireDate;
    }
}

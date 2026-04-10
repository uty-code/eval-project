package com.ees.eval.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사원 정보를 외부 계층에 전달하기 위한 불변 데이터 전송 객체(Record)입니다.
 * JOIN을 통해 가져온 직급명과 권한명 목록을 함께 포함합니다.
 *
 * @param empId 사원 고유 식별자
 * @param deptId 소속 부서 ID
 * @param positionId 직급 ID
 * @param username 로그인용 사용자 아이디
 * @param password BCrypt 암호화된 비밀번호 (조회 시 일반적으로 null 처리)
 * @param name 사원 성명
 * @param email 이메일 주소
 * @param hireDate 입사일
 * @param positionName JOIN으로 가져온 직급 명칭
 * @param roleNames JOIN으로 가져온 보유 권한 명칭 목록
 * @param isDeleted 삭제 여부 (y/n)
 * @param version 낙관적 락을 위한 버전 번호
 * @param createdAt 생성 일시
 * @param createdBy 생성자 ID
 * @param updatedAt 수정 일시
 * @param updatedBy 수정자 ID
 */
@Builder
public record EmployeeDTO(
    Long empId,
    Long deptId,
    Long positionId,
    String username,
    String password,
    String name,
    String email,
    LocalDate hireDate,
    String positionName,
    List<String> roleNames,
    String isDeleted,
    Integer version,
    LocalDateTime createdAt,
    Long createdBy,
    LocalDateTime updatedAt,
    Long updatedBy
) {}

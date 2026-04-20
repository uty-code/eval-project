package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 로그인 시도 이력(Audit Log)을 나타내는 도메인 클래스입니다.
 * 성공/실패 여부, 접속 IP, User-Agent 등을 기록합니다.
 */
@Getter
@Builder
public class LoginLog {

    private Long logId;

    /** 로그인 시도한 사원 ID (존재하지 않는 사번이면 null) */
    private Long empId;

    /** 실제 입력된 사번 원문 */
    private String loginInput;

    /**
     * 결과 코드
     * SUCCESS        : 로그인 성공
     * FAIL_INVALID   : 아이디 또는 비밀번호 불일치
     * FAIL_LOCKED    : 계정 잠금 (5회 초과)
     * FAIL_RETIRED   : 퇴사 계정
     * FAIL_PENDING   : 승인 대기 계정
     */
    private String resultCode;

    /** 접속 IP (IPv4/IPv6) */
    private String ipAddress;

    /** 브라우저/기기 정보 */
    private String userAgent;

    /** 로그인 실패 여부 ('y': 실패, 'n': 성공) */
    private String isFailure;

    private LocalDateTime createdAt;
}

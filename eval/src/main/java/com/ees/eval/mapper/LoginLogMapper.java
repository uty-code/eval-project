package com.ees.eval.mapper;

import com.ees.eval.domain.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 로그인 이력(Audit Log) MyBatis 매퍼 인터페이스입니다.
 */
@Mapper
public interface LoginLogMapper {

    /**
     * 로그인 이력 1건을 저장합니다.
     *
     * @param loginLog 저장할 로그인 이력 객체
     */
    void insert(LoginLog loginLog);

    /**
     * 특정 사원의 로그인 이력을 최신순으로 조회합니다.
     *
     * @param empId 사원 ID
     * @return 로그인 이력 목록
     */
    List<LoginLog> findByEmpId(Long empId);

    /**
     * 전체 로그인 이력을 최신순으로 조회합니다. (관리자용)
     *
     * @return 전체 로그인 이력 목록
     */
    List<LoginLog> findAll();

    /**
     * 마지막 로그인 성공 이후 연속 실패 횟수를 조회합니다.
     * 계정 잠금 여부 판단에 사용됩니다 (5회 이상이면 잠금).
     *
     * @param empId 대상 사원 ID
     * @return 연속 실패 횟수
     */
    int countRecentFailures(@Param("empId") Long empId);

    /**
     * 계정 잠금 해제 시 해당 사원의 실패 로그 is_failure를 'n'으로 초기화합니다.
     * 로그 자체는 삭제하지 않고 이력을 보존합니다.
     *
     * @param empId 대상 사원 ID
     * @return 업데이트된 행 수
     */
    int resetFailureLogsByEmpId(@Param("empId") Long empId);
}

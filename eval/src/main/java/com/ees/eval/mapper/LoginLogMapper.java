package com.ees.eval.mapper;

import com.ees.eval.domain.LoginLog;
import org.apache.ibatis.annotations.Mapper;

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
}

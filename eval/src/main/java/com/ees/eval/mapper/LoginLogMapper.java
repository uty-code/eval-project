package com.ees.eval.mapper;

import com.ees.eval.domain.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

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
     * 특정 사원의 로그인 이력을 페이징하여 조회합니다.
     *
     * @param empId 사원 ID
     * @param limit 페이지당 개수
     * @param offset 시작 위치
     * @param keyword 검색어 (IP 또는 입력값)
     * @return 로그인 이력 목록
     */
    List<LoginLog> findByEmpId(@Param("empId") Long empId, @Param("limit") int limit, @Param("offset") int offset, @Param("keyword") String keyword);

    /**
     * 특정 사원의 로그인 이력 총 개수를 조회합니다.
     *
     * @param empId 사원 ID
     * @param keyword 검색어
     * @return 총 개수
     */
    int countByEmpId(@Param("empId") Long empId, @Param("keyword") String keyword);

    /**
     * 전체 로그인 이력을 페이징하여 조회합니다. (관리자용)
     *
     * @param limit 페이지당 개수
     * @param offset 시작 위치
     * @param keyword 검색어 (IP 또는 사번)
     * @return 전체 로그인 이력 목록
     */
    List<LoginLog> findAll(@Param("limit") int limit, @Param("offset") int offset, @Param("keyword") String keyword);

    /**
     * 전체 로그인 이력 총 개수를 조회합니다.
     *
     * @param keyword 검색어
     * @return 총 개수
     */
    int countAll(@Param("keyword") String keyword);

    /**
     * 마지막 로그인 성공 이후 연속 실패 횟수를 조회합니다.
     * 계정 잠금 여부 판단에 사용됩니다 (5회 이상이면 잠금).
     *
     * @param empId 대상 사원 ID
     * @return 연속 실패 횟수
     */
    int countRecentFailures(@Param("empId") Long empId);

    /**
     * 다수의 사원에 대한 마지막 성공 이후 연속 실패 횟수를 한 번에 조회합니다. (N+1 방지)
     * 실패 기록이 없는 사원은 결과에 포함되지 않습니다 (0건으로 간주).
     *
     * @param empIds 대상 사원 ID 목록
     * @return 사원 ID(EMP_ID)와 실패 횟수(FAIL_COUNT)가 포함된 맵 리스트
     */
    List<Map<String, Object>> countRecentFailuresByEmpIds(@Param("empIds") List<Long> empIds);

    /**
     * 계정 잠금 해제 시 해당 사원의 실패 로그 is_failure를 'n'으로 초기화합니다.
     * 로그 자체는 삭제하지 않고 이력을 보존합니다.
     *
     * @param empId 대상 사원 ID
     * @return 업데이트된 행 수
     */
    int resetFailureLogsByEmpId(@Param("empId") Long empId);
}

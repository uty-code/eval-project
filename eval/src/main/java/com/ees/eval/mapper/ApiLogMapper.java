package com.ees.eval.mapper;

import com.ees.eval.domain.ApiLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * API 호출 이력 로그 매퍼 인터페이스입니다.
 * {@code api_logs_51} 테이블에 대한 데이터 접근을 담당합니다.
 */
@Mapper
public interface ApiLogMapper {

    /**
     * API 호출 이력 로그를 삽입합니다.
     *
     * @param apiLog 삽입할 API 로그 도메인 객체
     */
    void insertApiLog(ApiLog apiLog);

    /**
     * API 호출 이력 로그를 최신순으로 전체 조회합니다.
     *
     * @return API 로그 목록
     */
    java.util.List<ApiLog> selectAllApiLogs();

    /**
     * 다중 조건 검색 필터를 적용하여 API 로그를 페이징 조회합니다.
     * @param params 검색 조건 맵 (limit, offset 포함)
     * @return 검색된 API 로그 목록
     */
    java.util.List<ApiLog> searchApiLogs(java.util.Map<String, Object> params);

    /**
     * 다중 조건 검색 필터를 적용하여 API 로그의 총 개수를 조회합니다.
     * @param params 검색 조건 맵
     * @return 총 개수
     */
    int countApiLogs(java.util.Map<String, Object> params);
}

package com.ees.eval.service;

import com.ees.eval.domain.ApiLog;

/**
 * API 호출 이력 로그 서비스 인터페이스입니다.
 * AOP 계층에서 가로챈 API 호출 정보를 DB에 저장하는 역할을 담당합니다.
 */
public interface ApiLogService {

    /**
     * API 호출 이력 로그를 저장합니다.
     * 독립 트랜잭션(REQUIRES_NEW)으로 동작하여,
     * 로깅 실패가 본 비즈니스 로직에 영향을 주지 않습니다.
     *
     * @param apiLog 저장할 API 로그 도메인 객체
     */
    void saveLog(ApiLog apiLog);

    /**
     * API 호출 이력 로그를 최신순으로 전체 조회합니다.
     *
     * @return API 로그 목록
     */
    java.util.List<ApiLog> findAll();

    /**
     * 다중 조건 검색 필터를 적용하여 API 로그를 페이징 조회합니다.
     *
     * @param params 검색 조건 맵
     * @param page 현재 페이지
     * @param size 페이지 크기
     * @return 검색된 API 로그 목록 (페이징 객체)
     */
    com.ees.eval.dto.PageResponseDTO<ApiLog> searchLogs(java.util.Map<String, Object> params, int page, int size);
}

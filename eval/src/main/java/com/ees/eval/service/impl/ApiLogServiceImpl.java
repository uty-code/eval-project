package com.ees.eval.service.impl;

import com.ees.eval.domain.ApiLog;
import com.ees.eval.mapper.ApiLogMapper;
import com.ees.eval.service.ApiLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 호출 이력 로그 서비스 구현체입니다.
 *
 * <p>{@code Propagation.REQUIRES_NEW}를 적용하여 독립 트랜잭션으로 동작합니다.
 * 로깅 중 예외가 발생하더라도 본 비즈니스 트랜잭션(예: 평가 점수 저장)에
 * 영향을 주지 않도록 설계되었습니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLogServiceImpl implements ApiLogService {

    private final ApiLogMapper apiLogMapper;

    /**
     * API 호출 이력 로그를 DB에 저장합니다.
     * 독립 트랜잭션으로 동작하며, 내부 예외 발생 시 로그만 남기고 무시합니다.
     *
     * @param apiLog 저장할 API 로그 도메인 객체
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(ApiLog apiLog) {
        try {
            apiLogMapper.insertApiLog(apiLog);
        } catch (Exception e) {
            // 로깅 실패가 비즈니스 로직에 영향을 주면 안 되므로 경고만 출력
            log.warn("[API 로그] 저장 실패 - url: {}, error: {}", apiLog.getApiUrl(), e.getMessage());
        }
    }

    /**
     * API 호출 이력 로그를 최신순으로 전체 조회합니다.
     *
     * @return API 로그 목록
     */
    @Override
    @Transactional(readOnly = true)
    public java.util.List<ApiLog> findAll() {
        return apiLogMapper.selectAllApiLogs();
    }

    /**
     * 다중 조건 검색 필터를 적용하여 API 로그를 페이징 조회합니다.
     *
     * @param params 검색 조건 맵
     * @param page 현재 페이지
     * @param size 페이지 크기
     * @return 검색된 API 로그 목록 (페이징 객체)
     */
    @Override
    @Transactional(readOnly = true)
    public com.ees.eval.dto.PageResponseDTO<ApiLog> searchLogs(java.util.Map<String, Object> params, int page, int size) {
        int offset = (page - 1) * size;
        params.put("limit", size);
        params.put("offset", offset);
        
        java.util.List<ApiLog> list = apiLogMapper.searchApiLogs(params);
        int total = apiLogMapper.countApiLogs(params);
        
        return com.ees.eval.dto.PageResponseDTO.of(list, page, size, total);
    }
}

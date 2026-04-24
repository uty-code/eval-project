package com.ees.eval.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 프로젝트 내 모든 도메인(Entity) 테이블에 공통으로 들어가는
 * 감사(Audit), 삭제 상태 지시자(Soft delete), 버전(낙관적 락) 필드를 정의한 기본 추상 클래스입니다.
 */
@Getter
@Setter
public abstract class BaseEntity {
    private String isDeleted;
    private Integer version;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    /**
     * 엔티티가 최초 데이터베이스에 삽입(Persist)되기 전에 수동으로 호출되는 초기화 로직입니다.
     * 기본 비삭제 상태('n')와 시간, 최초 버전(0) 등을 셋팅합니다.
     */
    public void prePersist() {
        this.isDeleted = "n";
        this.version = 0;
        this.createdAt = LocalDateTime.now();
        this.createdBy = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        this.updatedAt = this.createdAt;
        this.updatedBy = this.createdBy;
    }

    /**
     * 엔티티가 데이터베이스에서 업데이트(Update)되기 전에 호출되는 최신화 로직입니다.
     * 수정 시간 정보만 갱신하며, 버전(version) 숫자는 MyBatis 쿼리 내에서 직접 증가시킵니다.
     */
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
    }
}

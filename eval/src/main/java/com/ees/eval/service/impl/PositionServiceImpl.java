package com.ees.eval.service.impl;

import com.ees.eval.domain.Position;
import com.ees.eval.dto.PositionDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.PositionMapper;
import com.ees.eval.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PositionService 구현체로 직급 관리 비즈니스 프로세스를 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionMapper positionMapper;

    /**
     * 단건 직급 상세 조회
     */
    @Override
    @Transactional(readOnly = true)
    public PositionDTO getPositionById(Long positionId) {
        // 매퍼 호출 후 데이터 유무에 따른 예외 처리 및 DTO 반환
        Position position = positionMapper.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position을 찾을 수 없습니다: " + positionId));
        return convertToDto(position);
    }

    /**
     * 전체 직급 리스트 패치
     */
    @Override
    @Transactional(readOnly = true)
    public List<PositionDTO> getAllPositions() {
        // 정렬된 전체 리스트를 조회하여 매핑
        return positionMapper.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 직급 신규 등록 프로세스
     */
    @Override
    @Transactional
    public PositionDTO createPosition(PositionDTO positionDto) {
        // 엔티티로 전환하여 데이터베이스 저장 준비 (감사 필드 주입)
        Position position = convertToEntity(positionDto);
        position.prePersist();

        // 데이터베이스 영속화 수행
        positionMapper.insert(position);

        return convertToDto(position);
    }

    /**
     * 직급 수정 로직 (동시성 제어 적용)
     */
    @Override
    @Transactional
    public PositionDTO updatePosition(PositionDTO positionDto) {
        // 업데이트 전 시간 정보 갱신 및 데이터 변환
        Position position = convertToEntity(positionDto);
        position.preUpdate();

        // MyBatis를 통해 행 단위 조건부 업데이트 실행
        int updatedRows = positionMapper.update(position);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("정보가 다른 사용자에 의해 변경되었거나 수정 충돌이 발생했습니다.");
        }

        // 수정 완료 후 최신화된 객체 정보 조회 및 결과 반환
        return getPositionById(position.getPositionId());
    }

    /**
     * 직급 삭제 로직 (논리 삭제)
     */
    @Override
    @Transactional
    public void deletePosition(Long positionId) {
        Long currentUserId = 1L; // 배포 시 컨텍스트 사용자 정보로 교체

        // 테이블에서 실제 행을 지우지 않고 상태값만 업데이트 처리
        int updatedRows = positionMapper.softDelete(positionId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 Position을 찾을 수 없습니다: " + positionId);
        }
    }

    /**
     * 내부 메서드: 도메인 엔티티를 레코드 기반 DTO로 변환
     */
    private PositionDTO convertToDto(Position position) {
        return PositionDTO.builder()
                .positionId(position.getPositionId())
                .positionName(position.getPositionName())
                .hierarchyLevel(position.getHierarchyLevel())
                .weightBase(position.getWeightBase())
                .isDeleted(position.getIsDeleted())
                .version(position.getVersion())
                .createdAt(position.getCreatedAt())
                .createdBy(position.getCreatedBy())
                .updatedAt(position.getUpdatedAt())
                .updatedBy(position.getUpdatedBy())
                .build();
    }

    /**
     * 내부 메서드: 외부 전달받은 DTO를 서비스 내부 엔티티로 변환
     */
    private Position convertToEntity(PositionDTO dto) {
        Position position = Position.builder()
                .positionId(dto.positionId())
                .positionName(dto.positionName())
                .hierarchyLevel(dto.hierarchyLevel())
                .weightBase(dto.weightBase())
                .build();
        position.setIsDeleted(dto.isDeleted());
        position.setVersion(dto.version());
        position.setCreatedAt(dto.createdAt());
        position.setCreatedBy(dto.createdBy());
        position.setUpdatedAt(dto.updatedAt());
        position.setUpdatedBy(dto.updatedBy());
        return position;
    }
}

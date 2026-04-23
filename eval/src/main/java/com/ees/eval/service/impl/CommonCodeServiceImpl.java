package com.ees.eval.service.impl;

import com.ees.eval.domain.CommonCode;
import com.ees.eval.dto.CommonCodeDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.CommonCodeMapper;
import com.ees.eval.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CommonCodeService의 실제 기능을 구현하는 비즈니스 구현체 클래스입니다.
 */
@Service
@RequiredArgsConstructor
public class CommonCodeServiceImpl implements CommonCodeService {

    private final CommonCodeMapper commonCodeMapper;

    /**
     * 상세 코드 단건 조회
     */
    @Override
    @Transactional(readOnly = true)
    public CommonCodeDTO getCodeById(Long codeId) {
        // 데이터 존재 여부를 체크하고 엔티티 패치 후 변환하여 반환
        CommonCode code = commonCodeMapper.findById(codeId)
                .orElseThrow(() -> new IllegalArgumentException("CommonCode를 찾을 수 없습니다: " + codeId));
        return convertToDto(code);
    }

    /**
     * 전체 코드 목록 조희
     */
    @Override
    @Transactional(readOnly = true)
    public List<CommonCodeDTO> getAllCodes() {
        // 마스터 리스트를 가져와 기록형 DTO로 가공 후 리턴
        return commonCodeMapper.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 그룹별 코드 필터 조회
     */
    @Override
    @Transactional(readOnly = true)
    public List<CommonCodeDTO> getCodesByGroupCode(String groupCode) {
        // 그룹 검색 조건에 맞는 데이터를 매스 패치하여 스트림 처리
        return commonCodeMapper.findByGroupCode(groupCode).stream().map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 코드 정보 신규 적재
     */
    @Override
    @Transactional
    public CommonCodeDTO createCode(CommonCodeDTO codeDto) {
        // 엔티티로 변환 후 시스템 레벨의 공통 데이터 초기화 대행
        CommonCode code = convertToEntity(codeDto);
        code.prePersist();

        // 매퍼를 통한 DB 삽입 (Identity값 확보 포함)
        commonCodeMapper.insert(code);

        return convertToDto(code);
    }

    /**
     * 코드 데이터 변경 프로세스 (버전 제어 포함)
     */
    @Override
    @Transactional
    public CommonCodeDTO updateCode(CommonCodeDTO codeDto) {
        // 엔티티 변환 시점 이전 최신화 일시 패치
        CommonCode code = convertToEntity(codeDto);
        code.preUpdate();

        // 동시 수정을 방지하기 위해 버전 필드를 대조한 수정을 수행
        int updatedRows = commonCodeMapper.update(code);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("정보가 다른 사용자에 의해 변경되었거나 수정 충돌이 발생했습니다.");
        }

        // 영속화된 최종 결과를 다시 읽어와 상위 계층에 응답
        return getCodeById(code.getCodeId());
    }

    /**
     * 코드 논리 삭제 수행
     */
    @Override
    @Transactional
    public void deleteCode(Long codeId) {
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();

        // 삭제 일시 및 수정자 정보를 담아 소프트 델리트 명령 하달
        int updatedRows = commonCodeMapper.softDelete(codeId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 CommonCode를 찾을 수 없습니다: " + codeId);
        }
    }

    /**
     * 엔티티 객체를 외부 노출용 레코드(DTO)로 변환합니다.
     */
    private CommonCodeDTO convertToDto(CommonCode code) {
        return CommonCodeDTO.builder()
                .codeId(code.getCodeId())
                .groupCode(code.getGroupCode())
                .codeValue(code.getCodeValue())
                .codeName(code.getCodeName())
                .description(code.getDescription())
                .isDeleted(code.getIsDeleted())
                .version(code.getVersion())
                .createdAt(code.getCreatedAt())
                .createdBy(code.getCreatedBy())
                .updatedAt(code.getUpdatedAt())
                .updatedBy(code.getUpdatedBy())
                .build();
    }

    /**
     * 외부로 받은 레코드(DTO) 정보를 비즈니스 처리 가능한 도메인 엔티티로 변환합니다.
     */
    private CommonCode convertToEntity(CommonCodeDTO dto) {
        CommonCode code = CommonCode.builder()
                .codeId(dto.codeId())
                .groupCode(dto.groupCode())
                .codeValue(dto.codeValue())
                .codeName(dto.codeName())
                .description(dto.description())
                .build();
        code.setIsDeleted(dto.isDeleted());
        code.setVersion(dto.version());
        code.setCreatedAt(dto.createdAt());
        code.setCreatedBy(dto.createdBy());
        code.setUpdatedAt(dto.updatedAt());
        code.setUpdatedBy(dto.updatedBy());
        return code;
    }
}

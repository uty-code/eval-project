package com.ees.eval.service;

import com.ees.eval.dto.PositionDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PositionServiceTest {

    @Autowired
    private PositionService positionService;

    @Test
    @DisplayName("Position 생성 및 락 테스트 (Record 기반)")
    void positionBasicTest() {
        PositionDTO dto = PositionDTO.builder()
                .positionName("사원")
                .hierarchyLevel(1)
                .weightBase(new BigDecimal("1.00"))
                .build();
        
        PositionDTO saved = positionService.createPosition(dto);
        assertThat(saved.positionId()).isNotNull();

        PositionDTO tx1 = positionService.getPositionById(saved.positionId());
        PositionDTO tx2 = positionService.getPositionById(saved.positionId());

        PositionDTO updatedTx1Attempt = PositionDTO.builder()
                .positionId(tx1.positionId())
                .positionName(tx1.positionName())
                .hierarchyLevel(2)
                .weightBase(tx1.weightBase())
                .isDeleted(tx1.isDeleted())
                .version(tx1.version())
                .createdAt(tx1.createdAt())
                .createdBy(tx1.createdBy())
                .build();
        positionService.updatePosition(updatedTx1Attempt);

        PositionDTO updatedTx2Attempt = PositionDTO.builder()
                .positionId(tx2.positionId())
                .positionName(tx2.positionName())
                .hierarchyLevel(3)
                .weightBase(tx2.weightBase())
                .isDeleted(tx2.isDeleted())
                .version(tx2.version())
                .createdAt(tx2.createdAt())
                .createdBy(tx2.createdBy())
                .build();

        assertThatThrownBy(() -> positionService.updatePosition(updatedTx2Attempt))
                .isInstanceOf(EesOptimisticLockException.class);
    }
}

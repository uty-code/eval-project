package com.ees.eval.service;

import com.ees.eval.dto.CommonCodeDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ees.eval.support.AbstractMssqlTest;

@SpringBootTest
@Transactional
class CommonCodeServiceTest extends AbstractMssqlTest {

    @Autowired
    private CommonCodeService commonCodeService;

    @Test
    @DisplayName("CommonCode 그룹 코드 조회 특화 기능 테스트 (Sequenced Collection 적용)")
    void groupCodeTest() {
        CommonCodeDTO dto1 = CommonCodeDTO.builder()
                .groupCode("EVAL_STEP")
                .codeValue("STEP1")
                .codeName("1차 평가")
                .build();
        CommonCodeDTO dto2 = CommonCodeDTO.builder()
                .groupCode("EVAL_STEP")
                .codeValue("STEP2")
                .codeName("2차 평가")
                .build();
        
        commonCodeService.createCode(dto1);
        commonCodeService.createCode(dto2);

        List<CommonCodeDTO> codes = commonCodeService.getCodesByGroupCode("EVAL_STEP");
        assertThat(codes.size()).isGreaterThanOrEqualTo(2);
        
        // Java 21: Sequenced Collections 메서드 사용
        CommonCodeDTO first = codes.getFirst();
        CommonCodeDTO last = codes.getLast();
        assertThat(first.codeValue()).isEqualTo("STEP1");
        assertThat(last.codeValue()).isEqualTo("STEP2");
    }

    @Test
    @DisplayName("낙관적 락 및 소프트 델리트 검증 (Pattern Matching & Unnamed Variables)")
    void concurrencyAndSoftDeleteTest() {
        CommonCodeDTO dto = CommonCodeDTO.builder()
                .groupCode("TMP_GROUP")
                .codeValue("VAL1")
                .codeName("임시")
                .build();
        CommonCodeDTO saved = commonCodeService.createCode(dto);

        CommonCodeDTO tx1 = commonCodeService.getCodeById(saved.codeId());
        CommonCodeDTO tx2 = commonCodeService.getCodeById(saved.codeId());

        CommonCodeDTO updatedTx1Attempt = CommonCodeDTO.builder()
                .codeId(tx1.codeId())
                .groupCode(tx1.groupCode())
                .codeValue(tx1.codeValue())
                .codeName("업데이트 됨")
                .description(tx1.description())
                .isDeleted(tx1.isDeleted())
                .version(tx1.version())
                .createdAt(tx1.createdAt())
                .createdBy(tx1.createdBy())
                .build();
        commonCodeService.updateCode(updatedTx1Attempt);

        CommonCodeDTO updatedTx2Attempt = CommonCodeDTO.builder()
                .codeId(tx2.codeId())
                .groupCode(tx2.groupCode())
                .codeValue(tx2.codeValue())
                .codeName("충돌 예정 업데이트")
                .description(tx2.description())
                .isDeleted(tx2.isDeleted())
                .version(tx2.version())
                .createdAt(tx2.createdAt())
                .createdBy(tx2.createdBy())
                .build();

        try {
            commonCodeService.updateCode(updatedTx2Attempt);
        } catch (Exception e) {
            // Java 21: Pattern Matching for switch 적용
            switch(e) {
                case EesOptimisticLockException oe -> assertThat(oe.getMessage()).contains("수정 충돌");
                case IllegalArgumentException _ -> throw new RuntimeException("Unexpected IllegalArgumentException");
                default -> throw new RuntimeException("Unexpected exception", e);
            }
        }

        // Soft delete test
        commonCodeService.deleteCode(saved.codeId());
        
        // Java 21: Unnamed Variables (_) 적용
        try {
            commonCodeService.getCodeById(saved.codeId());
        } catch (IllegalArgumentException _) {
            // expected exception, unnamed variable used
        }
    }
}

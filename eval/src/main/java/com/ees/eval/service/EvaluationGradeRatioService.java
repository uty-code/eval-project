package com.ees.eval.service;

import com.ees.eval.dto.EvaluationGradeRatioDTO;
import com.ees.eval.mapper.EvaluationGradeRatioMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvaluationGradeRatioService {

    private final EvaluationGradeRatioMapper ratioMapper;

    /**
     * 특정 차수/부서의 등급 비율을 조회합니다. 없으면 기본값(10,20,40,20,10)을 반환합니다.
     */
    public EvaluationGradeRatioDTO getGradeRatio(Long periodId, Long deptId) {
        // 1. 특정 부서 설정 조회
        EvaluationGradeRatioDTO ratio = ratioMapper.getGradeRatio(periodId, deptId);
        
        // 2. 부서 설정이 없고 특정 부서(deptId != null)를 조회 중이라면 전사 공통 설정 조회
        if (ratio == null && deptId != null) {
            EvaluationGradeRatioDTO commonRatio = ratioMapper.getGradeRatio(periodId, null);
            if (commonRatio != null) {
                // 전사 공통 설정이 있다면 '비율 값'들만 가져와서 현재 부서용 DTO로 재구성
                return EvaluationGradeRatioDTO.builder()
                        .periodId(periodId)
                        .deptId(deptId)
                        .gradeSRatio(commonRatio.gradeSRatio())
                        .gradeARatio(commonRatio.gradeARatio())
                        .gradeBRatio(commonRatio.gradeBRatio())
                        .gradeCRatio(commonRatio.gradeCRatio())
                        .gradeDRatio(commonRatio.gradeDRatio())
                        .build();
            }
        }
        
        // 3. 부서 설정도 없고 전사 공통 설정도 없으면 기본값 반환
        if (ratio == null) {
            return EvaluationGradeRatioDTO.defaultRatio(periodId, deptId);
        }
        return ratio;
    }

    /**
     * 등급 비율을 저장합니다. 저장 전 비율의 합이 100인지 검증합니다.
     */
    @Transactional
    public void saveGradeRatio(EvaluationGradeRatioDTO dto) {
        int sum = dto.gradeSRatio() + dto.gradeARatio() + dto.gradeBRatio()
                + dto.gradeCRatio() + dto.gradeDRatio();

        if (sum != 100) {
            throw new IllegalArgumentException("등급 비율의 합은 정확히 100%여야 합니다. (현재 합계: " + sum + "%)");
        }

        ratioMapper.upsertGradeRatio(dto);
    }

    /**
     * 특정 차수/부서의 등급 비율이 명시적으로 설정되어 있고, 합계가 100%인지 검증합니다.
     */
    public boolean isGradeRatioValid(Long periodId, Long deptId) {
        // 1. 부서별 설정 확인
        EvaluationGradeRatioDTO ratio = ratioMapper.getGradeRatio(periodId, deptId);

        // 2. 부서별 설정이 없다면 전사 공통 설정(deptId = null) 확인 (Fallback)
        if (ratio == null && deptId != null) {
            ratio = ratioMapper.getGradeRatio(periodId, null);
        }

        // 3. 둘 다 없다면 미설정으로 간주하여 실패
        if (ratio == null) {
            return false;
        }

        // 4. 합계가 100인지 검증
        int sum = ratio.gradeSRatio() + ratio.gradeARatio() + ratio.gradeBRatio()
                + ratio.gradeCRatio() + ratio.gradeDRatio();

        return sum == 100;
    }
}

package com.ees.eval.service.impl;

import com.ees.eval.domain.FinalGrade;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.FinalGradeMapper;
import com.ees.eval.service.FinalGradeProcessor;
import com.ees.eval.service.ScoreCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalGradeProcessorImpl implements FinalGradeProcessor {

    private final ScoreCalculationService scoreCalculationService;
    private final FinalGradeMapper finalGradeMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public void processGradeAndRanking(Long periodId, Long evaluateeId, Long deptId, String relationTypeCode, Long actorEmpId, boolean forceRelativeCalculation) {
        log.info("[FinalGradeProcessor] 시작 - periodId={}, evaluateeId={}, deptId={}, relation={}", periodId, evaluateeId, deptId, relationTypeCode);

        // 1. 총점 계산
        Integer totalScore = scoreCalculationService.calculateTotalScore(periodId, evaluateeId);
        log.info("[FinalGradeProcessor] 총점 산출 결과: {}", totalScore);

        if (totalScore == null) {
            log.info("[FinalGradeProcessor] 총점이 null이므로 저장을 건너뜁니다.");
            return;
        }

        // 2. FinalGrade 업데이트 (낙관적 락 버전 통일)
        finalGradeMapper.findByPeriodIdAndEmpId(periodId, evaluateeId).ifPresentOrElse(
            existing -> {
                existing.setTotalScore(totalScore);
                existing.setUpdatedAt(LocalDateTime.now());
                existing.setUpdatedBy(actorEmpId);
                finalGradeMapper.update(existing);
                log.info("[FinalGradeProcessor] 기존 FinalGrade 업데이트 완료 - gradeId={}", existing.getGradeId());
            },
            () -> {
                // 버그 수정 지점: 신규 데이터 버전을 0으로 일괄 고정하고 감사 필드를 모두 세팅합니다.
                FinalGrade fg = FinalGrade.builder()
                        .periodId(periodId)
                        .empId(evaluateeId)
                        .totalScore(totalScore)
                        .finalGradeCode("-") // 등급 대기
                        .isDeleted("n")
                        .version(0)          // 낙관적 락 충돌 방지 (모든 신규 레코드는 버전 0)
                        .createdAt(LocalDateTime.now())
                        .createdBy(actorEmpId)
                        .updatedAt(LocalDateTime.now())
                        .updatedBy(actorEmpId)
                        .build();
                finalGradeMapper.insert(fg);
                log.info("[FinalGradeProcessor] 신규 FinalGrade 생성 완료 (version=0)");
            }
        );

        // 3. 부서/본부 전체 등급 재산출 (상대평가)
        // 조건: 강제 산출 플래그가 켜져 있거나, 평가 관계가 2차 평가(EXECUTIVE)인 경우에만
        if (deptId != null && (forceRelativeCalculation || "EXECUTIVE".equals(relationTypeCode))) {
            boolean isLeader = departmentMapper.countDepartmentsByLeaderId(evaluateeId) > 0;
            if (isLeader) {
                departmentMapper.findById(deptId).ifPresent(dept -> {
                    if (dept.getParentDeptId() != null) {
                        log.info("[FinalGradeProcessor] 본부 내 팀장 상대평가 시작 - parentDeptId={}", dept.getParentDeptId());
                        scoreCalculationService.calculateRelativeGradesForLeadersInHQ(periodId, dept.getParentDeptId());
                        log.info("[FinalGradeProcessor] 본부 내 팀장 상대평가 완료");
                    } else {
                        log.warn("[FinalGradeProcessor] 팀장이지만 상위 부서(본부)가 없어 본부 내 팀장 상대평가 건너뜀 - deptId={}", deptId);
                    }
                });
            } else {
                log.info("[FinalGradeProcessor] 부서 단위 일반 사원 상대평가 시작 - deptId={}", deptId);
                scoreCalculationService.calculateRelativeGradesForDepartment(periodId, deptId);
                log.info("[FinalGradeProcessor] 부서 단위 상대평가 실시간 랭킹 산정 완료 - deptId={}", deptId);
            }
        } else {
            log.info("[FinalGradeProcessor] 상대평가 등급 재계산 건너뜀 (relationType={}, force={})", relationTypeCode, forceRelativeCalculation);
        }
    }
}

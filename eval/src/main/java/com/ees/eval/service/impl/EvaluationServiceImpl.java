package com.ees.eval.service.impl;

import com.ees.eval.domain.Evaluation;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 평가 데이터 제출 공통 로직 구현체입니다.
 * 4개 평가 컨트롤러(Performance, MyEvaluation, MultiDimensional, FinalGrade)에서
 * 동일하게 반복되던 elementId 파싱 + Upsert 로직을 통합합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationMapper evaluationMapper;

    @Override
    @Transactional
    public Set<Long> upsertEvaluations(Long mappingId, Map<String, String> params, Long empId) {
        // 1. elementId 추출 (comment_{id}, score_{id} 접두어 파싱)
        Set<Long> elementIds = new HashSet<>();
        params.keySet().forEach(key -> {
            if (key.startsWith("comment_") || key.startsWith("score_")) {
                try {
                    elementIds.add(Long.parseLong(key.substring(key.indexOf("_") + 1)));
                } catch (Exception ignore) {
                }
            }
        });

        // 2. 각 요소별 Upsert 처리
        for (Long elementId : elementIds) {
            String comment = params.get("comment_" + elementId);
            String scoreStr = params.get("score_" + elementId);

            Integer score = null;
            if (scoreStr != null && !scoreStr.trim().isEmpty()) {
                score = Integer.valueOf(scoreStr.trim()); // NumberFormatException → 호출부에서 처리
            }

            final Integer finalScore = score;
            final String finalComment = (comment != null) ? comment.trim() : "";

            evaluationMapper.findByMappingIdAndElementId(mappingId, elementId)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setReason(finalComment);
                                existing.setScore(finalScore);
                                existing.setConfirmStatusCode("SUBMITTED");
                                existing.preUpdate();
                                evaluationMapper.update(existing);
                            },
                            () -> {
                                Evaluation eval = Evaluation.builder()
                                        .mappingId(mappingId)
                                        .elementId(elementId)
                                        .confirmStatusCode("SUBMITTED")
                                        .build();
                                eval.setReason(finalComment);
                                eval.setScore(finalScore);
                                eval.prePersist();
                                eval.setCreatedBy(empId);
                                eval.setUpdatedBy(empId);
                                evaluationMapper.insert(eval);
                            });
        }

        return elementIds;
    }
}

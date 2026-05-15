/* 
 * [대시보드 성능 최적화 인덱스 전략]
 * 
 * 1. evaluations_51 (평가 상세 점수)
 *    - 피평가자/차수별 집계 시 mapping_id 및 element_id 조회가 빈번함
 */
CREATE INDEX IX_evaluations_51_mapping_element 
ON evaluations_51 (mapping_id, element_id) 
WHERE is_deleted = 'n';

/* 
 * 2. evaluator_mappings_51 (평가 관계 매핑)
 *    - 차수별, 평가자별 진행 현황 조회 최적화
 */
CREATE INDEX IX_evaluator_mappings_51_period_evaluator_type 
ON evaluator_mappings_51 (period_id, evaluator_id, relation_type_code) 
INCLUDE (evaluatee_id)
WHERE is_deleted = 'n';

/* 
 * 3. final_grades_51 (최종 등급)
 *    - 차수별 등급 분포 및 개인별 최근 등급 조회 최적화
 */
CREATE INDEX IX_final_grades_51_period_emp 
ON final_grades_51 (period_id, emp_id) 
INCLUDE (final_grade_code, total_score)
WHERE is_deleted = 'n';

/* 
 * 4. evaluation_elements_51 (평가 요소)
 *    - 차수별, 부서별 필수 평가 요소 개수 집계 최적화
 */
CREATE INDEX IX_evaluation_elements_51_period_dept 
ON evaluation_elements_51 (period_id, dept_id) 
WHERE is_deleted = 'n';

GO

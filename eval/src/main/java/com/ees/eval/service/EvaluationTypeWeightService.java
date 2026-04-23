package com.ees.eval.service;

import com.ees.eval.dto.EvaluationTypeWeightDTO;
import java.util.List;

public interface EvaluationTypeWeightService {
    List<EvaluationTypeWeightDTO> getTypeWeights(Long periodId, Long deptId, String targetRoleCode);
    void saveTypeWeights(Long periodId, Long deptId, String targetRoleCode, List<EvaluationTypeWeightDTO> weights);
}

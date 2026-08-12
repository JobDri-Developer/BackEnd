package com.jobdri.jobdri_api.domain.evaluation.analysis.port;

import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationAnalysisCommand;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationGeneratedResult;

public interface EvaluationAnalysisGenerator {
    EvaluationGeneratedResult generate(EvaluationAnalysisCommand command);
}

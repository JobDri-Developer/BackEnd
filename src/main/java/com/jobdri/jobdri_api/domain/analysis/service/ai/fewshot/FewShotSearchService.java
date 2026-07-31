package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import java.util.List;

public interface FewShotSearchService {
    List<SelectedFewShotCase> searchRelevantFewShots(FewShotSearchQuery query, int topK);
}

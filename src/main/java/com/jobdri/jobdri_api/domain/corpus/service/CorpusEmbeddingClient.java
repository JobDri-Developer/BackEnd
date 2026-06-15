package com.jobdri.jobdri_api.domain.corpus.service;

import java.util.List;

public interface CorpusEmbeddingClient {
    List<float[]> embed(List<String> texts);
}

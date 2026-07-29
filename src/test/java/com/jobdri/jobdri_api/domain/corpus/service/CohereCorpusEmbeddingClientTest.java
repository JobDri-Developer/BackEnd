package com.jobdri.jobdri_api.domain.corpus.service;

import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CohereCorpusEmbeddingClientTest {

    @Test
    @DisplayName("문서 임베딩은 전역 CohereEmbeddingClient의 embedDocuments에 위임한다")
    void delegatesDocumentEmbeddingToGlobalClient() {
        CohereEmbeddingClient globalClient = mock(CohereEmbeddingClient.class);
        List<String> texts = List.of("문서 1", "문서 2");
        List<float[]> embeddings = List.of(new float[]{1.0f}, new float[]{2.0f});
        when(globalClient.embedDocuments(texts)).thenReturn(embeddings);

        CohereCorpusEmbeddingClient client = new CohereCorpusEmbeddingClient(globalClient);

        assertThat(client.embed(texts, CorpusEmbeddingClient.InputType.SEARCH_DOCUMENT))
                .isSameAs(embeddings);
        verify(globalClient).embedDocuments(texts);
    }

    @Test
    @DisplayName("검색 쿼리 임베딩은 전역 CohereEmbeddingClient의 embedQuery에 위임한다")
    void delegatesQueryEmbeddingToGlobalClient() {
        CohereEmbeddingClient globalClient = mock(CohereEmbeddingClient.class);
        when(globalClient.embedQuery("검색 질의")).thenReturn(new float[]{1.0f, 2.0f});

        CohereCorpusEmbeddingClient client = new CohereCorpusEmbeddingClient(globalClient);

        List<float[]> result = client.embed(List.of("검색 질의"), CorpusEmbeddingClient.InputType.SEARCH_QUERY);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).containsExactly(1.0f, 2.0f);
        verify(globalClient).embedQuery("검색 질의");
    }
}

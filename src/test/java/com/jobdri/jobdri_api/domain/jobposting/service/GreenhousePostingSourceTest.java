package com.jobdri.jobdri_api.domain.jobposting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.jsoup.Connection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

class GreenhousePostingSourceTest {
    private static final String URL = "https://job-boards.greenhouse.io/example/jobs/123?gh_src=sample";
    private static final URI API = URI.create("https://boards-api.greenhouse.io/v1/boards/example/jobs/123");
    private final GreenhousePostingSource source = spy(new GreenhousePostingSource(new ObjectMapper()));
    private final Connection.Response response = mock(Connection.Response.class);

    /** Uses the public link identifiers to fetch a fixed API URL and converts escaped HTML to text. */
    @Test
    void readsTitleAndBodyFromOfficialEndpoint() throws Exception {
        stubResponse(200, """
                {"title":"Backend Engineer","content":"&lt;p&gt;Develop hiring APIs with Java &amp;amp; SQL.&lt;/p&gt;"}
                """);
        assertThat(source.read(URL)).isEqualTo("Backend Engineer\n\nDevelop hiring APIs with Java & SQL.");
        verify(source).fetch(API);
    }

    /** Keeps adjacent paragraphs and list items separated in the analysis input. */
    @Test
    void separatesAdjacentParagraphsAndListItems() throws Exception {
        stubResponse(200, """
                {"title":"Backend Engineer","content":"<p>Required</p><p>Skills</p><ul><li>Java</li><li>Python</li></ul>"}
                """);
        assertThat(source.read(URL)).isEqualTo("Backend Engineer\n\nRequired Skills Java Python");
    }

    /** Maps Java HTTP client timeouts to the same error as socket timeouts. */
    @Test
    void mapsHttpClientTimeout() throws Exception {
        doThrow(new HttpTimeoutException("Request timed out")).when(source).fetch(API);
        assertFailure(URL, GeneralErrorCode.POSTING_SOURCE_TIMEOUT);
    }

    /** Maps upstream failures without reading an error response as a posting. */
    @ParameterizedTest
    @CsvSource({
            "404, POSTING_SOURCE_NOT_FOUND", "403, POSTING_SOURCE_BLOCKED", "429, POSTING_SOURCE_BLOCKED",
            "302, POSTING_SOURCE_UNAVAILABLE", "500, POSTING_SOURCE_UNAVAILABLE"
    })
    void mapsUpstreamStatus(int status, GeneralErrorCode code) throws Exception {
        stubResponse(status, "");
        assertFailure(URL, code);
        verify(response, never()).bodyAsBytes();
    }

    /** Rejects missing content, oversized input, malformed payloads, and timeouts. */
    @Test
    void rejectsUnusableResponses() throws Exception {
        stubResponse(200, "{\"title\":\"Posting\",\"content\":\"&lt;p&gt;&lt;/p&gt;\"}");
        assertFailure(URL, GeneralErrorCode.POSTING_CONTENT_EMPTY);
        stubResponse(200, "{\"content\":\"" + "x".repeat(10_001) + "\"}");
        assertFailure(URL, GeneralErrorCode.POSTING_CONTENT_TOO_LARGE);
        stubResponse(200, "x".repeat(512 * 1024 + 1));
        assertFailure(URL, GeneralErrorCode.POSTING_CONTENT_TOO_LARGE);
        stubResponse(200, "not JSON");
        assertFailure(URL, GeneralErrorCode.POSTING_SOURCE_UNAVAILABLE);
        doThrow(new SocketTimeoutException()).when(source).fetch(API);
        assertFailure(URL, GeneralErrorCode.POSTING_SOURCE_TIMEOUT);
    }

    /** Rejects URLs outside the supported format before making an outbound request. */
    @ParameterizedTest
    @CsvSource({
            "https://job-boards.greenhouse.io/example, POSTING_URL_UNSUPPORTED",
            "https://example.com/example/jobs/123, POSTING_URL_UNSUPPORTED",
            "https://job-boards.greenhouse.io/example/jobs/%31, POSTING_URL_UNSUPPORTED",
            "http://job-boards.greenhouse.io/example/jobs/123, POSTING_URL_INVALID",
            "https://user@job-boards.greenhouse.io/example/jobs/123, POSTING_URL_INVALID"
    })
    void rejectsInvalidOrUnsupportedUrl(String url, GeneralErrorCode code) throws Exception {
        assertFailure(url, code);
        verify(source, never()).fetch(any());
    }

    /** Supplies a controlled HTTP status and payload for a fixed endpoint. */
    private void stubResponse(int status, String json) throws Exception {
        doReturn(response).when(source).fetch(API);
        when(response.statusCode()).thenReturn(status);
        when(response.bodyAsBytes()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Asserts the application's error code for the supplied input. */
    private void assertFailure(String url, GeneralErrorCode code) {
        assertThatExceptionOfType(GeneralException.class)
                .isThrownBy(() -> source.read(url))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(code));
    }
}

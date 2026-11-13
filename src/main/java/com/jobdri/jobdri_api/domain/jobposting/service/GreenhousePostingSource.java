package com.jobdri.jobdri_api.domain.jobposting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.regex.Pattern;

import static com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode.*;

@Component
@RequiredArgsConstructor
public class GreenhousePostingSource {
    private static final String PUBLIC_HOST = "job-boards.greenhouse.io";
    private static final String API_ROOT = "https://boards-api.greenhouse.io/v1/boards";
    private static final Pattern DETAIL_PATH = Pattern.compile("/([a-zA-Z0-9_.-]+)/jobs/([0-9]+)/?");
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final int TIMEOUT_MILLIS = 5_000;
    private final ObjectMapper mapper;

    /** Reads a public Greenhouse posting into the text format used by registration preview. */
    public String read(String postingUrl) {
        URI endpoint = endpoint(postingUrl);
        try {
            Connection.Response response = fetch(endpoint);
            switch (response.statusCode()) {
                case 200 -> { }
                case 404, 410 -> throw new GeneralException(POSTING_SOURCE_NOT_FOUND);
                case 401, 403, 429 -> throw new GeneralException(POSTING_SOURCE_BLOCKED);
                default -> throw new GeneralException(POSTING_SOURCE_UNAVAILABLE);
            }
            byte[] bytes = response.bodyAsBytes();
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new GeneralException(POSTING_CONTENT_TOO_LARGE);
            }
            JsonNode posting = mapper.readTree(bytes);
            if (posting == null || !posting.isObject()) {
                throw new GeneralException(POSTING_SOURCE_UNAVAILABLE);
            }
            String title = posting.path("title").asText("").strip();
            String body = Jsoup.parseBodyFragment(
                    Parser.unescapeEntities(posting.path("content").asText(""), false)).body().text();
            if (JobPostingIngestInputValidator.nonWhitespaceLength(body)
                    < JobPostingIngestInputValidator.MIN_RAW_TEXT_NON_WHITESPACE_LENGTH) {
                throw new GeneralException(POSTING_CONTENT_EMPTY);
            }
            String text = title.isEmpty() ? body : title + "\n\n" + body;
            if (text.length() > JobPostingIngestInputValidator.MAX_RAW_TEXT_LENGTH) {
                throw new GeneralException(POSTING_CONTENT_TOO_LARGE);
            }
            return text;
        } catch (SocketTimeoutException | HttpTimeoutException e) {
            throw new GeneralException(POSTING_SOURCE_TIMEOUT);
        } catch (IOException e) {
            throw new GeneralException(POSTING_SOURCE_UNAVAILABLE);
        }
    }

    /** Maps a supported public detail link to the fixed official API host. */
    private URI endpoint(String postingUrl) {
        URI url;
        try {
            url = URI.create(postingUrl);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(POSTING_URL_INVALID);
        }
        if (postingUrl.length() > 2048 || !"https".equalsIgnoreCase(url.getScheme())
                || url.getHost() == null || url.getRawUserInfo() != null || url.getPort() != -1) {
            throw new GeneralException(POSTING_URL_INVALID);
        }
        var path = DETAIL_PATH.matcher(url.getRawPath());
        if (!PUBLIC_HOST.equalsIgnoreCase(url.getHost()) || !path.matches()
                || path.group(1).equals(".") || path.group(1).equals("..")) {
            throw new GeneralException(POSTING_URL_UNSUPPORTED);
        }
        return URI.create(API_ROOT + "/" + path.group(1) + "/jobs/" + path.group(2));
    }

    /** Fetches a bounded response from the official endpoint without following redirects. */
    protected Connection.Response fetch(URI endpoint) throws IOException {
        return Jsoup.connect(endpoint.toString())
                .header("Accept", "application/json")
                .timeout(TIMEOUT_MILLIS)
                .maxBodySize(MAX_RESPONSE_BYTES + 1)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .execute()
                .readFully();
    }
}

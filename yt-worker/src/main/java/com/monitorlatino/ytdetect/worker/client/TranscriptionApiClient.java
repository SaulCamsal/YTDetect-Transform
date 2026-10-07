package com.monitorlatino.ytdetect.worker.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TranscriptionApiClient {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionApiClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public TranscriptionApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${external.transcription.url:https://transcript.monitorlatino.com/api/v1}") String baseUrl,
            @Value("${external.transcription.api-key:default-transcription-key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public TranscriptionJobResponse submitTranscription(TranscriptionJobRequest request) {
        log.info("Submitting audio to transcription API: stationId={}, audioUrl={}",
                request.stationId(), request.audioUrl());

        return restClient.post()
                .uri("/transcriptions")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TranscriptionJobResponse.class);
    }
}

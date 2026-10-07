package com.monitorlatino.ytdetect.api.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public TranscriptionClient(
            RestClient.Builder restClientBuilder,
            @Value("${external.transcription.url:https://transcript.monitorlatino.com/api/v1}") String baseUrl,
            @Value("${external.transcription.api-key:default-transcription-key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public List<StationTranscriptionItemDto> fetchStationTranscriptions(Integer stationId, LocalDate date) {
        log.debug("Fetching station transcriptions for stationId: {} date: {}", stationId, date);
        try {
            List<StationTranscriptionItemDto> result = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stations/{stationId}/transcriptions")
                            .queryParam("date", date.toString())
                            .queryParam("includeText", "false")
                            .build(stationId))
                    .header("X-API-Key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return result != null ? result : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch station transcriptions for station {} on {}: {}", stationId, date, e.getMessage(), e);
            return List.of();
        }
    }

    public Optional<TranscriptionJobResultDto> fetchJobResult(UUID jobId) {
        log.debug("Fetching transcription job result for jobId: {}", jobId);
        try {
            TranscriptionJobResultDto result = restClient.get()
                    .uri("/transcriptions/{jobId}/result", jobId)
                    .header("X-API-Key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TranscriptionJobResultDto.class);

            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("Failed to fetch transcription result for jobId {}: {}", jobId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}

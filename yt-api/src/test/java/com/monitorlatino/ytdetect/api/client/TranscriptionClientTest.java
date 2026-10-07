package com.monitorlatino.ytdetect.api.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TranscriptionClientTest {

    private TranscriptionClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TranscriptionClient(builder, "https://transcript.example.com/api/v1", "test-api-key");
    }

    @Test
    @DisplayName("Should fetch station transcriptions for date")
    void shouldFetchStationTranscriptions() {
        UUID jobId = UUID.randomUUID();
        String json = "[{\"jobId\":\"" + jobId + "\",\"status\":\"COMPLETED\",\"audioUrl\":\"s3://bucket/audio.mp3\"}]";

        mockServer.expect(requestTo("https://transcript.example.com/api/v1/stations/101/transcriptions?date=2026-10-06&includeText=false"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<StationTranscriptionItemDto> items = client.fetchStationTranscriptions(101, LocalDate.of(2026, 10, 6));

        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals(jobId, items.get(0).jobId());
        assertEquals("COMPLETED", items.get(0).status());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should fetch job result with segments")
    void shouldFetchJobResult() {
        UUID jobId = UUID.randomUUID();
        String json = """
                {
                  "jobId": "%s",
                  "status": "COMPLETED",
                  "segments": [
                    {"start": 0.0, "end": 4.5, "text": "Hola mundo"},
                    {"start": 4.5, "end": 9.0, "text": "segunda parte"}
                  ]
                }
                """.formatted(jobId);

        mockServer.expect(requestTo("https://transcript.example.com/api/v1/transcriptions/" + jobId + "/result"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TranscriptionJobResultDto> resultOpt = client.fetchJobResult(jobId);

        assertTrue(resultOpt.isPresent());
        TranscriptionJobResultDto result = resultOpt.get();
        assertEquals(jobId, result.jobId());
        assertEquals(2, result.segments().size());
        assertEquals("Hola mundo", result.segments().get(0).text());
        mockServer.verify();
    }
}

package com.monitorlatino.ytdetect.worker.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TranscriptionApiClientTest {

    private TranscriptionApiClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TranscriptionApiClient(builder, "https://transcript.example.com/api/v1", "secret-api-key");
    }

    @Test
    @DisplayName("Should submit transcription job and parse UUID response")
    void shouldSubmitTranscriptionJob() {
        UUID expectedJobId = UUID.randomUUID();
        String jsonResponse = "{\"jobId\":\"" + expectedJobId + "\",\"duplicate\":false}";

        mockServer.expect(requestTo("https://transcript.example.com/api/v1/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "secret-api-key"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        TranscriptionJobRequest request = TranscriptionJobRequest.of(101, "s3://bucket/audio.mp3", "America/Mexico_City", "es");
        TranscriptionJobResponse response = client.submitTranscription(request);

        assertNotNull(response);
        assertEquals(expectedJobId, response.jobId());
        assertFalse(response.duplicate());
        mockServer.verify();
    }
}

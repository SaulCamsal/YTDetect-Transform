package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.common.dto.AudioArrivalMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AudioArrivalQueueServiceTest {

    private final SqsTemplate sqsTemplate = mock(SqsTemplate.class);
    private final AudioArrivalQueueService service = new AudioArrivalQueueService(sqsTemplate, "audio-arrivals");

    @Test
    @DisplayName("Should send audio arrival message to configured queue")
    void shouldSendAudioArrivalMessage() {
        UUID jobId = UUID.randomUUID();
        AudioArrivalMessage message = AudioArrivalMessage.of(
                jobId,
                6321,
                "s3://monitorlatino/CHART/YT/MILENIO/20261014/20261014_120000.mp3",
                "20261014_120000.mp3",
                "2026-10-14T12:00:00",
                "America/Mexico_City",
                "es"
        );

        service.sendAudioArrival(message);

        verify(sqsTemplate).send("audio-arrivals", message);
        assertEquals("audio-arrivals", service.getQueueName());
    }
}

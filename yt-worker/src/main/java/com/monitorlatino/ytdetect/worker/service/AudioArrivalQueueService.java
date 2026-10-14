package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.common.dto.AudioArrivalMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "yt.sqs.enabled", havingValue = "true", matchIfMissing = false)
public class AudioArrivalQueueService {

    private static final Logger log = LoggerFactory.getLogger(AudioArrivalQueueService.class);

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public AudioArrivalQueueService(
            SqsTemplate sqsTemplate,
            @Value("${yt.sqs.audio-arrivals-queue:audio-arrivals}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    public void sendAudioArrival(AudioArrivalMessage message) {
        log.info("Sending audio arrival for station {} (audio: {}) to SQS queue: {}",
                message.stationId(), message.audioUrl(), queueName);
        sqsTemplate.send(queueName, message);
    }

    public String getQueueName() {
        return queueName;
    }
}

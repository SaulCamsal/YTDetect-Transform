package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class YoutubeDownloadQueueService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeDownloadQueueService.class);

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public YoutubeDownloadQueueService(
            SqsTemplate sqsTemplate,
            @Value("${yt.sqs.download-queue:youtube-download-queue}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    public void enqueueDownloadJob(DownloadJobMessage message) {
        log.info("Sending video {} (channel: {}) to SQS queue: {}",
                message.videoId(), message.channelId(), queueName);
        sqsTemplate.send(queueName, message);
    }
}

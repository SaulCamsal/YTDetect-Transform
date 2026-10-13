package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class YoutubeDownloadQueueService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeDownloadQueueService.class);

    public YoutubeDownloadQueueService() {
    }

    public void enqueueDownloadJob(DownloadJobMessage message) {
        log.info("Video {} (channel: {}) registered in database with status QUEUED_DOWNLOAD for worker pickup",
                message.videoId(), message.channelId());
    }
}

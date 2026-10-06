package com.monitorlatino.ytdetect.api.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoLiveStreamingDetails;
import com.monitorlatino.ytdetect.api.config.YoutubeConfig;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.LiveStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "yt.live-watcher.enabled", havingValue = "true", matchIfMissing = true)
public class LiveStreamWatcher {

    private static final Logger log = LoggerFactory.getLogger(LiveStreamWatcher.class);
    private static final int MAX_DURATION_SECONDS = 36000; // 10 hours

    private final YouTube youTube;
    private final YoutubeConfig youtubeConfig;
    private final YoutubeVideoRepository videoRepository;
    private final YoutubeDownloadQueueService queueService;

    public LiveStreamWatcher(
            YouTube youTube,
            YoutubeConfig youtubeConfig,
            YoutubeVideoRepository videoRepository,
            YoutubeDownloadQueueService queueService) {
        this.youTube = youTube;
        this.youtubeConfig = youtubeConfig;
        this.videoRepository = videoRepository;
        this.queueService = queueService;
    }

    @Scheduled(fixedDelayString = "${yt.live-watcher.delay:600000}")
    @SchedulerLock(name = "youtube_live_stream_watcher", lockAtMostFor = "9m", lockAtLeastFor = "1m")
    @Transactional
    public void checkWaitingLiveStreams() {
        List<YoutubeVideo> waitingVideos = videoRepository.findByStatus(VideoStatus.WAITING_LIVE);
        if (waitingVideos.isEmpty()) {
            return;
        }

        log.info("Checking {} live streams currently in WAITING_LIVE status", waitingVideos.size());
        Map<String, YoutubeVideo> videoMap = waitingVideos.stream()
                .collect(Collectors.toMap(YoutubeVideo::getVideoId, Function.identity(), (a, b) -> a));

        List<String> videoIds = waitingVideos.stream()
                .map(YoutubeVideo::getVideoId)
                .toList();

        int batchSize = 50;
        for (int i = 0; i < videoIds.size(); i += batchSize) {
            List<String> batch = videoIds.subList(i, Math.min(i + batchSize, videoIds.size()));
            try {
                YouTube.Videos.List request = youTube.videos()
                        .list(List.of("snippet", "contentDetails", "liveStreamingDetails"))
                        .setId(batch);

                if (youtubeConfig.getApiKey() != null && !youtubeConfig.getApiKey().isBlank()) {
                    request.setKey(youtubeConfig.getApiKey());
                }

                VideoListResponse response = request.execute();
                if (response.getItems() != null) {
                    for (Video ytVideo : response.getItems()) {
                        YoutubeVideo videoEntity = videoMap.get(ytVideo.getId());
                        if (videoEntity != null) {
                            evaluateLiveVideo(videoEntity, ytVideo);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Failed to query live stream status from YouTube API: {}", e.getMessage(), e);
            }
        }
    }

    private void evaluateLiveVideo(YoutubeVideo entity, Video ytVideo) {
        VideoLiveStreamingDetails liveDetails = ytVideo.getLiveStreamingDetails();
        String broadcastContent = ytVideo.getSnippet() != null ? ytVideo.getSnippet().getLiveBroadcastContent() : "none";

        int durationSeconds = 0;
        if (ytVideo.getContentDetails() != null && ytVideo.getContentDetails().getDuration() != null) {
            try {
                durationSeconds = (int) Duration.parse(ytVideo.getContentDetails().getDuration()).toSeconds();
            } catch (Exception e) {
                log.warn("Could not parse duration for live video {}: {}", entity.getVideoId(), ytVideo.getContentDetails().getDuration());
            }
        }
        entity.setDurationSeconds(durationSeconds);

        boolean hasEnded = (liveDetails != null && liveDetails.getActualEndTime() != null)
                || "none".equalsIgnoreCase(broadcastContent);

        if (hasEnded && durationSeconds > 0) {
            if (durationSeconds > MAX_DURATION_SECONDS) {
                entity.setStatus(VideoStatus.SKIPPED);
                entity.setLiveStatus(LiveStatus.NONE);
                entity.setLastErrorCode("DURATION_LIMIT_EXCEEDED");
                entity.setLastErrorMessage("Live stream duration " + durationSeconds + "s exceeds limit of 36000s (10h)");
                videoRepository.save(entity);
                log.info("Live stream {} has ended but exceeds 10 hours ({}s), marked as SKIPPED",
                        entity.getVideoId(), durationSeconds);
            } else {
                entity.setStatus(VideoStatus.QUEUED_DOWNLOAD);
                entity.setLiveStatus(LiveStatus.NONE);
                videoRepository.save(entity);
                queueService.enqueueDownloadJob(DownloadJobMessage.of(entity.getVideoId(), entity.getChannel().getChannelId()));
                log.info("Live stream {} has ended with duration {}s. Status updated to QUEUED_DOWNLOAD and sent to SQS",
                        entity.getVideoId(), durationSeconds);
            }
        } else {
            if ("live".equalsIgnoreCase(broadcastContent)) {
                entity.setLiveStatus(LiveStatus.LIVE);
            } else if ("upcoming".equalsIgnoreCase(broadcastContent)) {
                entity.setLiveStatus(LiveStatus.UPCOMING);
            }
            videoRepository.save(entity);
            log.debug("Video {} is still live/upcoming (broadcast: {})", entity.getVideoId(), broadcastContent);
        }
    }
}

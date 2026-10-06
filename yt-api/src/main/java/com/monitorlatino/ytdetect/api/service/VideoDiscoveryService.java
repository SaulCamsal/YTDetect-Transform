package com.monitorlatino.ytdetect.api.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.monitorlatino.ytdetect.api.config.YoutubeConfig;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.LiveStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class VideoDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(VideoDiscoveryService.class);
    private static final int MAX_DURATION_SECONDS = 36000; // 10 hours

    private final YouTube youTube;
    private final YoutubeConfig youtubeConfig;
    private final YoutubeVideoRepository videoRepository;
    private final YoutubeChannelRepository channelRepository;
    private final YoutubeDownloadQueueService queueService;
    private final Clock clock;

    public VideoDiscoveryService(
            YouTube youTube,
            YoutubeConfig youtubeConfig,
            YoutubeVideoRepository videoRepository,
            YoutubeChannelRepository channelRepository,
            YoutubeDownloadQueueService queueService,
            Clock clock) {
        this.youTube = youTube;
        this.youtubeConfig = youtubeConfig;
        this.videoRepository = videoRepository;
        this.channelRepository = channelRepository;
        this.queueService = queueService;
        this.clock = clock;
    }

    public List<YoutubeVideo> discoverVideos(List<String> videoIds, DiscoveredVia discoveredVia) {
        if (videoIds == null || videoIds.isEmpty()) {
            return List.of();
        }

        List<String> toFetch = new ArrayList<>();
        for (String id : videoIds) {
            if (!videoRepository.existsByVideoId(id)) {
                toFetch.add(id);
            } else {
                log.debug("Video {} already registered in database, skipping discovery", id);
            }
        }

        if (toFetch.isEmpty()) {
            return List.of();
        }

        List<YoutubeVideo> processedVideos = new ArrayList<>();
        int batchSize = 50;

        for (int i = 0; i < toFetch.size(); i += batchSize) {
            List<String> batch = toFetch.subList(i, Math.min(i + batchSize, toFetch.size()));
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
                        processSingleVideo(ytVideo, discoveredVia)
                                .ifPresent(processedVideos::add);
                    }
                }
            } catch (IOException e) {
                log.error("Failed to fetch video details from YouTube API: {}", e.getMessage(), e);
            }
        }

        return processedVideos;
    }

    @Transactional
    public Optional<YoutubeVideo> processSingleVideo(Video ytVideo, DiscoveredVia discoveredVia) {
        String videoId = ytVideo.getId();
        if (videoRepository.existsByVideoId(videoId)) {
            return Optional.empty();
        }

        String channelId = ytVideo.getSnippet() != null ? ytVideo.getSnippet().getChannelId() : null;
        if (channelId == null) {
            log.warn("Video {} has no channelId in snippet, skipping", videoId);
            return Optional.empty();
        }

        Optional<YoutubeChannel> channelOpt = channelRepository.findByChannelId(channelId);
        if (channelOpt.isEmpty()) {
            log.warn("Channel {} for video {} is not registered, skipping", channelId, videoId);
            return Optional.empty();
        }

        YoutubeChannel channel = channelOpt.get();
        String title = ytVideo.getSnippet() != null ? ytVideo.getSnippet().getTitle() : null;
        String description = ytVideo.getSnippet() != null ? ytVideo.getSnippet().getDescription() : null;

        Instant publishedAt = null;
        if (ytVideo.getSnippet() != null && ytVideo.getSnippet().getPublishedAt() != null) {
            publishedAt = Instant.ofEpochMilli(ytVideo.getSnippet().getPublishedAt().getValue());
        }

        int durationSeconds = 0;
        if (ytVideo.getContentDetails() != null && ytVideo.getContentDetails().getDuration() != null) {
            try {
                durationSeconds = (int) Duration.parse(ytVideo.getContentDetails().getDuration()).toSeconds();
            } catch (Exception e) {
                log.warn("Could not parse duration for video {}: {}", videoId, ytVideo.getContentDetails().getDuration());
            }
        }

        String broadcastContent = ytVideo.getSnippet() != null ? ytVideo.getSnippet().getLiveBroadcastContent() : "none";
        LiveStatus liveStatus = LiveStatus.NONE;
        VideoStatus status;

        if ("live".equalsIgnoreCase(broadcastContent)) {
            liveStatus = LiveStatus.LIVE;
            status = VideoStatus.WAITING_LIVE;
        } else if ("upcoming".equalsIgnoreCase(broadcastContent)) {
            liveStatus = LiveStatus.UPCOMING;
            status = VideoStatus.WAITING_LIVE;
        } else if (durationSeconds > MAX_DURATION_SECONDS) {
            status = VideoStatus.SKIPPED;
            log.info("Video {} exceeds 10 hours limit ({} seconds), status = SKIPPED", videoId, durationSeconds);
        } else {
            status = VideoStatus.QUEUED_DOWNLOAD;
        }

        YoutubeVideo video = new YoutubeVideo(videoId, channel, title, status, discoveredVia);
        video.setDescription(description);
        video.setPublishedAt(publishedAt);
        video.setDurationSeconds(durationSeconds);
        video.setLiveStatus(liveStatus);
        video.setCreatedAt(clock.instant());

        if (status == VideoStatus.SKIPPED) {
            video.setLastErrorCode("DURATION_LIMIT_EXCEEDED");
            video.setLastErrorMessage("Video duration " + durationSeconds + "s exceeds limit of 36000s (10h)");
        }

        try {
            YoutubeVideo saved = videoRepository.save(video);
            log.info("Saved discovered video: {} status: {} channel: {}", videoId, status, channelId);

            if (status == VideoStatus.QUEUED_DOWNLOAD) {
                queueService.enqueueDownloadJob(DownloadJobMessage.of(videoId, channelId));
            }

            return Optional.of(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition: video {} was already saved by another process: {}", videoId, e.getMessage());
            return Optional.empty();
        }
    }
}

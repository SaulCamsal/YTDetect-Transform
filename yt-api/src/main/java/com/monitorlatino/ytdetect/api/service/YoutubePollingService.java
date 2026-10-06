package com.monitorlatino.ytdetect.api.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.monitorlatino.ytdetect.api.config.YoutubeConfig;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@ConditionalOnProperty(name = "yt.polling.enabled", havingValue = "true", matchIfMissing = true)
public class YoutubePollingService {

    private static final Logger log = LoggerFactory.getLogger(YoutubePollingService.class);
    private static final long MAX_PLAYLIST_ITEMS = 10L;

    private final YouTube youTube;
    private final YoutubeConfig youtubeConfig;
    private final YoutubeChannelRepository channelRepository;
    private final VideoDiscoveryService videoDiscoveryService;
    private final Clock clock;

    public YoutubePollingService(
            YouTube youTube,
            YoutubeConfig youtubeConfig,
            YoutubeChannelRepository channelRepository,
            VideoDiscoveryService videoDiscoveryService,
            Clock clock) {
        this.youTube = youTube;
        this.youtubeConfig = youtubeConfig;
        this.channelRepository = channelRepository;
        this.videoDiscoveryService = videoDiscoveryService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${yt.polling.delay:1800000}")
    @SchedulerLock(name = "youtube_polling", lockAtMostFor = "15m", lockAtLeastFor = "1m")
    public void pollActiveChannels() {
        log.info("Starting YouTube backup polling job");
        List<YoutubeChannel> activeChannels = channelRepository.findByEnabledTrue();

        for (YoutubeChannel channel : activeChannels) {
            try {
                pollChannel(channel);
            } catch (Exception e) {
                log.error("Error polling channel {}: {}", channel.getChannelId(), e.getMessage(), e);
            }
        }
        log.info("Finished YouTube backup polling job. Checked {} channels.", activeChannels.size());
    }

    public void pollChannel(YoutubeChannel channel) {
        String playlistId = channel.getUploadsPlaylistId();
        if (playlistId == null || playlistId.isBlank()) {
            log.debug("Channel {} has no uploads playlist ID configured, skipping polling", channel.getChannelId());
            return;
        }

        try {
            YouTube.PlaylistItems.List request = youTube.playlistItems()
                    .list(List.of("snippet", "contentDetails"))
                    .setPlaylistId(playlistId)
                    .setMaxResults(MAX_PLAYLIST_ITEMS);

            if (youtubeConfig.getApiKey() != null && !youtubeConfig.getApiKey().isBlank()) {
                request.setKey(youtubeConfig.getApiKey());
            }

            PlaylistItemListResponse response = request.execute();
            if (response.getItems() != null && !response.getItems().isEmpty()) {
                List<String> videoIds = new ArrayList<>();
                for (PlaylistItem item : response.getItems()) {
                    String videoId = null;
                    if (item.getContentDetails() != null && item.getContentDetails().getVideoId() != null) {
                        videoId = item.getContentDetails().getVideoId();
                    } else if (item.getSnippet() != null && item.getSnippet().getResourceId() != null) {
                        videoId = item.getSnippet().getResourceId().getVideoId();
                    }
                    if (videoId != null && !videoId.isBlank()) {
                        videoIds.add(videoId);
                    }
                }

                if (!videoIds.isEmpty()) {
                    log.debug("Found {} video IDs in playlist {} for channel {}", videoIds.size(), playlistId, channel.getChannelId());
                    videoDiscoveryService.discoverVideos(videoIds, DiscoveredVia.POLLING);
                }
            }

            channel.setLastCheckAt(clock.instant());
            channelRepository.save(channel);
        } catch (IOException e) {
            log.error("Failed to query playlist items for channel {}: {}", channel.getChannelId(), e.getMessage(), e);
        }
    }
}

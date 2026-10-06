package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.YoutubeApiClient;
import com.monitorlatino.ytdetect.api.client.dto.DiscoveredChannelInfo;
import com.monitorlatino.ytdetect.api.dto.SyncResultResponse;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class YoutubeSubscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeSubscriptionSyncService.class);

    private final YoutubeApiClient youtubeApiClient;
    private final YoutubeChannelRepository channelRepository;
    private final Clock clock;

    public YoutubeSubscriptionSyncService(
            YoutubeApiClient youtubeApiClient,
            YoutubeChannelRepository channelRepository,
            Clock clock) {
        this.youtubeApiClient = youtubeApiClient;
        this.channelRepository = channelRepository;
        this.clock = clock;
    }

    @Transactional
    public SyncResultResponse syncSubscriptions() {
        log.info("Starting YouTube subscription synchronization");
        try {
            List<DiscoveredChannelInfo> channels = youtubeApiClient.fetchAllSubscribedChannels();
            int discoveredCount = channels.size();
            int newlyAddedCount = 0;
            int updatedCount = 0;
            Instant now = clock.instant();

            for (DiscoveredChannelInfo info : channels) {
                Optional<YoutubeChannel> existingOpt = channelRepository.findByChannelId(info.channelId());
                if (existingOpt.isPresent()) {
                    YoutubeChannel existing = existingOpt.get();
                    boolean modified = false;

                    if (info.title() != null && !info.title().equals(existing.getChannelName())) {
                        existing.setChannelName(info.title());
                        modified = true;
                    }
                    if (info.uploadsPlaylistId() != null && !info.uploadsPlaylistId().equals(existing.getUploadsPlaylistId())) {
                        existing.setUploadsPlaylistId(info.uploadsPlaylistId());
                        modified = true;
                    }

                    existing.setLastCheckAt(now);
                    existing.setUpdatedAt(now);
                    channelRepository.save(existing);
                    if (modified) {
                        updatedCount++;
                    }
                } else {
                    YoutubeChannel newChannel = new YoutubeChannel(info.channelId(), info.title(), ChannelOrigin.SUBSCRIPTION);
                    newChannel.setUploadsPlaylistId(info.uploadsPlaylistId());
                    newChannel.setEnabled(false);
                    newChannel.setCreatedAt(now);
                    newChannel.setLastCheckAt(now);
                    channelRepository.save(newChannel);
                    newlyAddedCount++;
                }
            }

            log.info("YouTube subscription sync finished. Discovered: {}, Added: {}, Updated: {}",
                    discoveredCount, newlyAddedCount, updatedCount);

            return new SyncResultResponse("COMPLETED", discoveredCount, newlyAddedCount, updatedCount, "Sync successful");
        } catch (IOException e) {
            log.error("Failed to sync subscriptions from YouTube API: {}", e.getMessage(), e);
            return new SyncResultResponse("FAILED", 0, 0, 0, "Error: " + e.getMessage());
        }
    }

    @Scheduled(cron = "${yt.sync.cron:0 0 3 * * *}")
    @SchedulerLock(name = "youtube_subscription_sync", lockAtMostFor = "15m", lockAtLeastFor = "1m")
    public void scheduledSync() {
        log.info("Running scheduled YouTube subscription sync");
        syncSubscriptions();
    }
}

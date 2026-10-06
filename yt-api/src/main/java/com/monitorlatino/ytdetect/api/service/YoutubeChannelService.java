package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.YoutubeApiClient;
import com.monitorlatino.ytdetect.api.client.dto.DiscoveredChannelInfo;
import com.monitorlatino.ytdetect.api.dto.ChannelResponse;
import com.monitorlatino.ytdetect.api.dto.CreateChannelRequest;
import com.monitorlatino.ytdetect.api.dto.SyncResultResponse;
import com.monitorlatino.ytdetect.api.dto.UpdateChannelRequest;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Service
public class YoutubeChannelService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeChannelService.class);

    private final YoutubeChannelRepository channelRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final YoutubeSubscriptionSyncService syncService;
    private final Clock clock;

    public YoutubeChannelService(
            YoutubeChannelRepository channelRepository,
            YoutubeApiClient youtubeApiClient,
            YoutubeSubscriptionSyncService syncService,
            Clock clock) {
        this.channelRepository = channelRepository;
        this.youtubeApiClient = youtubeApiClient;
        this.syncService = syncService;
        this.clock = clock;
    }

    @Transactional
    public ChannelResponse createChannel(CreateChannelRequest request) {
        if (channelRepository.existsByChannelId(request.channelId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel already exists with id: " + request.channelId());
        }

        String channelName = request.channelName();
        String uploadsPlaylistId = null;

        try {
            DiscoveredChannelInfo info = youtubeApiClient.fetchChannelInfo(request.channelId());
            if (info != null) {
                if (channelName == null || channelName.isBlank()) {
                    channelName = info.title();
                }
                uploadsPlaylistId = info.uploadsPlaylistId();
            }
        } catch (IOException e) {
            log.warn("Could not enrich channel {} from YouTube API: {}", request.channelId(), e.getMessage());
        }

        YoutubeChannel channel = new YoutubeChannel(request.channelId(), channelName, ChannelOrigin.MANUAL);
        channel.setUploadsPlaylistId(uploadsPlaylistId);
        channel.setStationId(request.stationId());

        if (request.timezone() != null && !request.timezone().isBlank()) {
            channel.setTimezone(request.timezone());
        }
        if (request.languageCode() != null && !request.languageCode().isBlank()) {
            channel.setLanguageCode(request.languageCode());
        }

        Instant now = clock.instant();
        channel.setCreatedAt(now);
        channel.setEnabled(request.stationId() != null);

        YoutubeChannel saved = channelRepository.save(channel);
        log.info("Created manual YouTube channel: {} (id: {})", saved.getChannelId(), saved.getId());
        return ChannelResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<ChannelResponse> getChannels(Boolean enabled, Pageable pageable) {
        if (enabled != null) {
            return channelRepository.findAll((root, query, cb) -> cb.equal(root.get("enabled"), enabled), pageable)
                    .map(ChannelResponse::fromEntity);
        }
        return channelRepository.findAll(pageable).map(ChannelResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ChannelResponse getChannelById(Long id) {
        YoutubeChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found with id: " + id));
        return ChannelResponse.fromEntity(channel);
    }

    @Transactional
    public ChannelResponse updateChannel(Long id, UpdateChannelRequest request) {
        YoutubeChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found with id: " + id));

        if (request.enabled() != null) {
            channel.setEnabled(request.enabled());
        }
        if (request.stationId() != null) {
            channel.setStationId(request.stationId());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            channel.setTimezone(request.timezone());
        }
        if (request.languageCode() != null && !request.languageCode().isBlank()) {
            channel.setLanguageCode(request.languageCode());
        }

        channel.setUpdatedAt(clock.instant());
        YoutubeChannel updated = channelRepository.save(channel);
        log.info("Updated YouTube channel id: {}", updated.getId());
        return ChannelResponse.fromEntity(updated);
    }

    public SyncResultResponse triggerSync() {
        return syncService.syncSubscriptions();
    }
}

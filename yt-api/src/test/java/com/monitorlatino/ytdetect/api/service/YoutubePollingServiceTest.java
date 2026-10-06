package com.monitorlatino.ytdetect.api.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemContentDetails;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.monitorlatino.ytdetect.api.config.YoutubeConfig;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YoutubePollingServiceTest {

    @Mock
    private YouTube youTube;
    @Mock
    private YoutubeChannelRepository channelRepository;
    @Mock
    private VideoDiscoveryService videoDiscoveryService;

    private Clock fixedClock;
    private YoutubeConfig youtubeConfig;
    private YoutubePollingService pollingService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
        youtubeConfig = new YoutubeConfig();
        pollingService = new YoutubePollingService(
                youTube,
                youtubeConfig,
                channelRepository,
                videoDiscoveryService,
                fixedClock
        );
    }

    @Test
    @DisplayName("Should poll playlist items for active channel and trigger video discovery")
    void shouldPollPlaylistItemsAndTriggerDiscovery() throws IOException {
        YoutubeChannel channel = new YoutubeChannel("UC_ACTIVE", "Active Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setEnabled(true);
        channel.setUploadsPlaylistId("UU_ACTIVE");

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(channel));

        YouTube.PlaylistItems playlistItems = mock(YouTube.PlaylistItems.class);
        YouTube.PlaylistItems.List listRequest = mock(YouTube.PlaylistItems.List.class);

        when(youTube.playlistItems()).thenReturn(playlistItems);
        when(playlistItems.list(anyList())).thenReturn(listRequest);
        when(listRequest.setPlaylistId("UU_ACTIVE")).thenReturn(listRequest);
        when(listRequest.setMaxResults(10L)).thenReturn(listRequest);

        PlaylistItemListResponse response = new PlaylistItemListResponse();
        PlaylistItem item1 = new PlaylistItem().setContentDetails(new PlaylistItemContentDetails().setVideoId("vid_p1"));
        PlaylistItem item2 = new PlaylistItem().setContentDetails(new PlaylistItemContentDetails().setVideoId("vid_p2"));
        response.setItems(List.of(item1, item2));

        when(listRequest.execute()).thenReturn(response);

        pollingService.pollActiveChannels();

        verify(videoDiscoveryService).discoverVideos(List.of("vid_p1", "vid_p2"), DiscoveredVia.POLLING);
        verify(channelRepository).save(channel);
    }

    @Test
    @DisplayName("Should skip channel without uploads playlist ID")
    void shouldSkipChannelWithoutUploadsPlaylistId() {
        YoutubeChannel channel = new YoutubeChannel("UC_NO_PLAYLIST", "No Playlist", ChannelOrigin.MANUAL);
        channel.setEnabled(true);
        channel.setUploadsPlaylistId(null);

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(channel));

        pollingService.pollActiveChannels();

        verify(videoDiscoveryService, never()).discoverVideos(any(), any());
    }
}

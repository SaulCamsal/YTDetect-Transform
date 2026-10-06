package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.YoutubeApiClient;
import com.monitorlatino.ytdetect.api.client.dto.DiscoveredChannelInfo;
import com.monitorlatino.ytdetect.api.dto.SyncResultResponse;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YoutubeSubscriptionSyncServiceTest {

    @Mock
    private YoutubeApiClient youtubeApiClient;

    @Mock
    private YoutubeChannelRepository channelRepository;

    private Clock fixedClock;
    private YoutubeSubscriptionSyncService syncService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("UTC"));
        syncService = new YoutubeSubscriptionSyncService(youtubeApiClient, channelRepository, fixedClock);
    }

    @Test
    @DisplayName("Should add new channels when subscriptions are found")
    void shouldAddNewChannels() throws IOException {
        DiscoveredChannelInfo channel1 = new DiscoveredChannelInfo("UC_NEW_1", "Channel One", "UU_NEW_1");
        DiscoveredChannelInfo channel2 = new DiscoveredChannelInfo("UC_NEW_2", "Channel Two", "UU_NEW_2");
        when(youtubeApiClient.fetchAllSubscribedChannels()).thenReturn(List.of(channel1, channel2));
        when(channelRepository.findByChannelId(anyString())).thenReturn(Optional.empty());

        SyncResultResponse result = syncService.syncSubscriptions();

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.discoveredCount()).isEqualTo(2);
        assertThat(result.newlyAddedCount()).isEqualTo(2);
        assertThat(result.updatedCount()).isEqualTo(0);

        ArgumentCaptor<YoutubeChannel> captor = ArgumentCaptor.forClass(YoutubeChannel.class);
        verify(channelRepository, times(2)).save(captor.capture());

        List<YoutubeChannel> savedChannels = captor.getAllValues();
        assertThat(savedChannels).hasSize(2);
        assertThat(savedChannels.get(0).getChannelId()).isEqualTo("UC_NEW_1");
        assertThat(savedChannels.get(0).getOrigin()).isEqualTo(ChannelOrigin.SUBSCRIPTION);
        assertThat(savedChannels.get(0).isEnabled()).isFalse();
        assertThat(savedChannels.get(0).getUploadsPlaylistId()).isEqualTo("UU_NEW_1");
    }

    @Test
    @DisplayName("Should update existing channel uploadsPlaylistId and name")
    void shouldUpdateExistingChannels() throws IOException {
        DiscoveredChannelInfo updatedInfo = new DiscoveredChannelInfo("UC_EXISTING", "Updated Title", "UU_NEW_UPLOADS");
        when(youtubeApiClient.fetchAllSubscribedChannels()).thenReturn(List.of(updatedInfo));

        YoutubeChannel existing = new YoutubeChannel("UC_EXISTING", "Old Title", ChannelOrigin.SUBSCRIPTION);
        existing.setUploadsPlaylistId("UU_OLD_UPLOADS");
        when(channelRepository.findByChannelId("UC_EXISTING")).thenReturn(Optional.of(existing));

        SyncResultResponse result = syncService.syncSubscriptions();

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.discoveredCount()).isEqualTo(1);
        assertThat(result.newlyAddedCount()).isEqualTo(0);
        assertThat(result.updatedCount()).isEqualTo(1);

        verify(channelRepository).save(existing);
        assertThat(existing.getChannelName()).isEqualTo("Updated Title");
        assertThat(existing.getUploadsPlaylistId()).isEqualTo("UU_NEW_UPLOADS");
        assertThat(existing.getLastCheckAt()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
    }

    @Test
    @DisplayName("Should handle IOException from YouTube API gracefully")
    void shouldHandleIOException() throws IOException {
        when(youtubeApiClient.fetchAllSubscribedChannels()).thenThrow(new IOException("API Quota exceeded"));

        SyncResultResponse result = syncService.syncSubscriptions();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.discoveredCount()).isEqualTo(0);
        assertThat(result.message()).contains("API Quota exceeded");
        verify(channelRepository, never()).save(any());
    }
}

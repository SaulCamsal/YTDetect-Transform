package com.monitorlatino.ytdetect.api.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.monitorlatino.ytdetect.api.config.YoutubeConfig;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.LiveStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoDiscoveryServiceTest {

    @Mock
    private YouTube youTube;
    @Mock
    private YoutubeVideoRepository videoRepository;
    @Mock
    private YoutubeChannelRepository channelRepository;
    @Mock
    private YoutubeDownloadQueueService queueService;

    private Clock fixedClock;
    private YoutubeConfig youtubeConfig;
    private VideoDiscoveryService service;
    private YoutubeChannel channel;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
        youtubeConfig = new YoutubeConfig();
        service = new VideoDiscoveryService(
                youTube,
                youtubeConfig,
                videoRepository,
                channelRepository,
                queueService,
                fixedClock
        );

        channel = new YoutubeChannel("UC_TEST_CHAN", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setEnabled(true);
    }

    @Test
    @DisplayName("Should process standard video <= 10h and queue to SQS")
    void shouldProcessStandardVideoAndQueueToSqs() {
        Video ytVideo = createMockYtVideo("vid_standard", "UC_TEST_CHAN", "PT2H15M", "none");

        when(videoRepository.existsByVideoId("vid_standard")).thenReturn(false);
        when(channelRepository.findByChannelId("UC_TEST_CHAN")).thenReturn(Optional.of(channel));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<YoutubeVideo> result = service.processSingleVideo(ytVideo, DiscoveredVia.WEBSUB);

        assertTrue(result.isPresent());
        YoutubeVideo saved = result.get();
        assertEquals("vid_standard", saved.getVideoId());
        assertEquals(VideoStatus.QUEUED_DOWNLOAD, saved.getStatus());
        assertEquals(8100, saved.getDurationSeconds()); // 2h 15m = 8100s
        assertEquals(LiveStatus.NONE, saved.getLiveStatus());

        verify(queueService).enqueueDownloadJob(DownloadJobMessage.of("vid_standard", "UC_TEST_CHAN"));
    }

    @Test
    @DisplayName("Should skip video exceeding 10 hours limit")
    void shouldSkipVideoExceedingTenHours() {
        // PT11H = 39600s > 36000s
        Video ytVideo = createMockYtVideo("vid_long", "UC_TEST_CHAN", "PT11H", "none");

        when(videoRepository.existsByVideoId("vid_long")).thenReturn(false);
        when(channelRepository.findByChannelId("UC_TEST_CHAN")).thenReturn(Optional.of(channel));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<YoutubeVideo> result = service.processSingleVideo(ytVideo, DiscoveredVia.POLLING);

        assertTrue(result.isPresent());
        YoutubeVideo saved = result.get();
        assertEquals(VideoStatus.SKIPPED, saved.getStatus());
        assertEquals(39600, saved.getDurationSeconds());
        assertEquals("DURATION_LIMIT_EXCEEDED", saved.getLastErrorCode());

        verify(queueService, never()).enqueueDownloadJob(any());
    }

    @Test
    @DisplayName("Should mark live stream as WAITING_LIVE and not queue to SQS")
    void shouldMarkLiveStreamAsWaitingLive() {
        Video ytVideo = createMockYtVideo("vid_live", "UC_TEST_CHAN", "P0D", "live");

        when(videoRepository.existsByVideoId("vid_live")).thenReturn(false);
        when(channelRepository.findByChannelId("UC_TEST_CHAN")).thenReturn(Optional.of(channel));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<YoutubeVideo> result = service.processSingleVideo(ytVideo, DiscoveredVia.WEBSUB);

        assertTrue(result.isPresent());
        YoutubeVideo saved = result.get();
        assertEquals(VideoStatus.WAITING_LIVE, saved.getStatus());
        assertEquals(LiveStatus.LIVE, saved.getLiveStatus());

        verify(queueService, never()).enqueueDownloadJob(any());
    }

    @Test
    @DisplayName("Should mark upcoming stream as WAITING_LIVE and not queue to SQS")
    void shouldMarkUpcomingStreamAsWaitingLive() {
        Video ytVideo = createMockYtVideo("vid_upcoming", "UC_TEST_CHAN", "P0D", "upcoming");

        when(videoRepository.existsByVideoId("vid_upcoming")).thenReturn(false);
        when(channelRepository.findByChannelId("UC_TEST_CHAN")).thenReturn(Optional.of(channel));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<YoutubeVideo> result = service.processSingleVideo(ytVideo, DiscoveredVia.WEBSUB);

        assertTrue(result.isPresent());
        YoutubeVideo saved = result.get();
        assertEquals(VideoStatus.WAITING_LIVE, saved.getStatus());
        assertEquals(LiveStatus.UPCOMING, saved.getLiveStatus());

        verify(queueService, never()).enqueueDownloadJob(any());
    }

    @Test
    @DisplayName("Should ignore video if already present in database")
    void shouldIgnoreVideoIfAlreadyPresent() {
        Video ytVideo = createMockYtVideo("vid_exists", "UC_TEST_CHAN", "PT10M", "none");
        when(videoRepository.existsByVideoId("vid_exists")).thenReturn(true);

        Optional<YoutubeVideo> result = service.processSingleVideo(ytVideo, DiscoveredVia.WEBSUB);

        assertFalse(result.isPresent());
        verify(channelRepository, never()).findByChannelId(any());
        verify(videoRepository, never()).save(any());
        verify(queueService, never()).enqueueDownloadJob(any());
    }

    @Test
    @DisplayName("Should handle duplicate key race condition gracefully")
    void shouldHandleDuplicateKeyRaceConditionGracefully() {
        Video ytVideo = createMockYtVideo("vid_race", "UC_TEST_CHAN", "PT10M", "none");

        when(videoRepository.existsByVideoId("vid_race")).thenReturn(false);
        when(channelRepository.findByChannelId("UC_TEST_CHAN")).thenReturn(Optional.of(channel));
        when(videoRepository.save(any(YoutubeVideo.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        Optional<YoutubeVideo> result = service.processSingleVideo(ytVideo, DiscoveredVia.POLLING);

        assertFalse(result.isPresent());
        verify(queueService, never()).enqueueDownloadJob(any());
    }

    private Video createMockYtVideo(String videoId, String channelId, String durationIso, String liveBroadcastContent) {
        Video video = new Video();
        video.setId(videoId);

        VideoSnippet snippet = new VideoSnippet();
        snippet.setChannelId(channelId);
        snippet.setTitle("Sample Video " + videoId);
        snippet.setDescription("Sample Description");
        snippet.setLiveBroadcastContent(liveBroadcastContent);
        snippet.setPublishedAt(new DateTime(1696500000000L));
        video.setSnippet(snippet);

        VideoContentDetails contentDetails = new VideoContentDetails();
        contentDetails.setDuration(durationIso);
        video.setContentDetails(contentDetails);

        return video;
    }
}

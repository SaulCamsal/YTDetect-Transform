package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.dto.RetryVideosResponse;
import com.monitorlatino.ytdetect.api.dto.VideoResponse;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YoutubeVideoServiceTest {

    @Mock
    private YoutubeVideoRepository videoRepository;

    @Mock
    private YoutubeAudioChunkRepository chunkRepository;

    private Clock clock;
    private YoutubeVideoService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
        service = new YoutubeVideoService(videoRepository, chunkRepository, clock);
    }

    @Test
    @DisplayName("Should reset all failed videos to QUEUED_DOWNLOAD")
    void shouldRetryAllFailedVideos() {
        YoutubeChannel channel = new YoutubeChannel("UC123", "Test Channel", ChannelOrigin.MANUAL);
        YoutubeVideo v1 = new YoutubeVideo("vid1", channel, "Video 1", VideoStatus.FAILED, DiscoveredVia.WEBSUB);
        v1.setAttemptCount(3);
        v1.setLastErrorCode("YTDLP_ERROR");
        v1.setLastErrorMessage("Download error");

        YoutubeVideo v2 = new YoutubeVideo("vid2", channel, "Video 2", VideoStatus.FAILED, DiscoveredVia.POLLING);
        v2.setAttemptCount(3);
        v2.setLastErrorCode("FFMPEG_ERROR");
        v2.setLastErrorMessage("Transcode error");

        when(videoRepository.findByStatus(VideoStatus.FAILED)).thenReturn(List.of(v1, v2));

        RetryVideosResponse response = service.retryAllFailedVideos();

        assertEquals(2, response.retriedCount());
        assertEquals(List.of("vid1", "vid2"), response.videoIds());

        assertEquals(VideoStatus.QUEUED_DOWNLOAD, v1.getStatus());
        assertEquals(0, v1.getAttemptCount());
        assertNull(v1.getLastErrorCode());
        assertNull(v1.getLastErrorMessage());
        assertEquals(Instant.parse("2026-10-22T12:00:00Z"), v1.getUpdatedAt());

        assertEquals(VideoStatus.QUEUED_DOWNLOAD, v2.getStatus());
        assertEquals(0, v2.getAttemptCount());

        verify(videoRepository).saveAll(List.of(v1, v2));
    }

    @Test
    @DisplayName("Should retry single video by videoId")
    void shouldRetrySingleVideo() {
        YoutubeChannel channel = new YoutubeChannel("UC123", "Test Channel", ChannelOrigin.MANUAL);
        YoutubeVideo v = new YoutubeVideo("vid1", channel, "Video 1", VideoStatus.FAILED, DiscoveredVia.WEBSUB);
        v.setAttemptCount(3);
        v.setLastErrorCode("YTDLP_ERROR");
        v.setLastErrorMessage("Network error");

        when(videoRepository.findByVideoId("vid1")).thenReturn(Optional.of(v));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<VideoResponse> opt = service.retryVideo("vid1");

        assertTrue(opt.isPresent());
        VideoResponse response = opt.get();
        assertEquals("vid1", response.videoId());
        assertEquals(VideoStatus.QUEUED_DOWNLOAD, response.status());
        assertEquals(0, response.attemptCount());
        assertNull(response.lastErrorCode());
        assertNull(response.lastErrorMessage());
    }

    @Test
    @DisplayName("Should return empty optional when retrying non-existent video")
    void shouldReturnEmptyWhenVideoNotFound() {
        when(videoRepository.findByVideoId("non_existent")).thenReturn(Optional.empty());

        Optional<VideoResponse> opt = service.retryVideo("non_existent");

        assertTrue(opt.isEmpty());
    }
}

package com.monitorlatino.ytdetect.api.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoLiveStreamingDetails;
import com.google.api.services.youtube.model.VideoSnippet;
import com.monitorlatino.ytdetect.api.config.YoutubeConfig;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.LiveStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveStreamWatcherTest {

    @Mock
    private YouTube youTube;
    @Mock
    private YoutubeVideoRepository videoRepository;
    @Mock
    private YoutubeDownloadQueueService queueService;

    private YoutubeConfig youtubeConfig;
    private LiveStreamWatcher watcher;
    private YoutubeChannel channel;

    @BeforeEach
    void setUp() {
        youtubeConfig = new YoutubeConfig();
        watcher = new LiveStreamWatcher(
                youTube,
                youtubeConfig,
                videoRepository,
                queueService
        );

        channel = new YoutubeChannel("UC_LIVE_CHAN", "Live Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setEnabled(true);
    }

    @Test
    @DisplayName("Should detect finished live stream <= 10h and queue to SQS")
    void shouldDetectFinishedLiveStreamAndQueueToSqs() throws IOException {
        YoutubeVideo entity = new YoutubeVideo("vid_stream_done", channel, "Live Stream Done", VideoStatus.WAITING_LIVE, DiscoveredVia.WEBSUB);
        entity.setLiveStatus(LiveStatus.LIVE);

        when(videoRepository.findByStatus(VideoStatus.WAITING_LIVE)).thenReturn(List.of(entity));

        YouTube.Videos videos = mock(YouTube.Videos.class);
        YouTube.Videos.List listRequest = mock(YouTube.Videos.List.class);

        when(youTube.videos()).thenReturn(videos);
        when(videos.list(anyList())).thenReturn(listRequest);
        when(listRequest.setId(List.of("vid_stream_done"))).thenReturn(listRequest);

        Video ytVideo = new Video();
        ytVideo.setId("vid_stream_done");
        VideoSnippet snippet = new VideoSnippet().setLiveBroadcastContent("none");
        ytVideo.setSnippet(snippet);

        VideoContentDetails contentDetails = new VideoContentDetails().setDuration("PT1H45M"); // 6300s
        ytVideo.setContentDetails(contentDetails);

        VideoLiveStreamingDetails liveDetails = new VideoLiveStreamingDetails()
                .setActualEndTime(new DateTime(1696503600000L));
        ytVideo.setLiveStreamingDetails(liveDetails);

        VideoListResponse response = new VideoListResponse().setItems(List.of(ytVideo));
        when(listRequest.execute()).thenReturn(response);

        watcher.checkWaitingLiveStreams();

        assertEquals(VideoStatus.QUEUED_DOWNLOAD, entity.getStatus());
        assertEquals(LiveStatus.NONE, entity.getLiveStatus());
        assertEquals(6300, entity.getDurationSeconds());

        verify(videoRepository).save(entity);
        verify(queueService).enqueueDownloadJob(DownloadJobMessage.of("vid_stream_done", "UC_LIVE_CHAN"));
    }

    @Test
    @DisplayName("Should skip finished live stream exceeding 10h")
    void shouldSkipFinishedLiveStreamExceedingTenHours() throws IOException {
        YoutubeVideo entity = new YoutubeVideo("vid_long_stream", channel, "Very Long Live Stream", VideoStatus.WAITING_LIVE, DiscoveredVia.WEBSUB);
        entity.setLiveStatus(LiveStatus.LIVE);

        when(videoRepository.findByStatus(VideoStatus.WAITING_LIVE)).thenReturn(List.of(entity));

        YouTube.Videos videos = mock(YouTube.Videos.class);
        YouTube.Videos.List listRequest = mock(YouTube.Videos.List.class);

        when(youTube.videos()).thenReturn(videos);
        when(videos.list(anyList())).thenReturn(listRequest);
        when(listRequest.setId(List.of("vid_long_stream"))).thenReturn(listRequest);

        Video ytVideo = new Video();
        ytVideo.setId("vid_long_stream");
        VideoSnippet snippet = new VideoSnippet().setLiveBroadcastContent("none");
        ytVideo.setSnippet(snippet);

        VideoContentDetails contentDetails = new VideoContentDetails().setDuration("PT12H00M"); // 43200s
        ytVideo.setContentDetails(contentDetails);

        VideoLiveStreamingDetails liveDetails = new VideoLiveStreamingDetails()
                .setActualEndTime(new DateTime(1696543200000L));
        ytVideo.setLiveStreamingDetails(liveDetails);

        VideoListResponse response = new VideoListResponse().setItems(List.of(ytVideo));
        when(listRequest.execute()).thenReturn(response);

        watcher.checkWaitingLiveStreams();

        assertEquals(VideoStatus.SKIPPED, entity.getStatus());
        assertEquals(43200, entity.getDurationSeconds());
        assertEquals("DURATION_LIMIT_EXCEEDED", entity.getLastErrorCode());

        verify(videoRepository).save(entity);
        verify(queueService, never()).enqueueDownloadJob(any());
    }
}

package com.monitorlatino.ytdetect.worker.listener;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import com.monitorlatino.ytdetect.worker.client.TranscriptionApiClient;
import com.monitorlatino.ytdetect.worker.client.TranscriptionJobResponse;
import com.monitorlatino.ytdetect.worker.service.AudioArrivalQueueService;
import com.monitorlatino.ytdetect.worker.service.AudioProcessingException;
import com.monitorlatino.ytdetect.worker.service.AudioProcessingService;
import com.monitorlatino.ytdetect.worker.service.S3AudioUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadJobListenerTest {

    @Mock
    private YoutubeVideoRepository videoRepository;
    @Mock
    private YoutubeAudioChunkRepository chunkRepository;
    @Mock
    private AudioProcessingService audioProcessingService;
    @Mock
    private S3AudioUploadService s3AudioUploadService;
    @Mock
    private AudioArrivalQueueService audioArrivalQueueService;
    @Mock
    private TranscriptionApiClient transcriptionApiClient;

    private Clock fixedClock;
    private DownloadJobListener listener;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);
        listener = new DownloadJobListener(
                videoRepository,
                chunkRepository,
                audioProcessingService,
                s3AudioUploadService,
                Optional.of(audioArrivalQueueService),
                transcriptionApiClient,
                fixedClock,
                null,
                3
        );
    }

    @Test
    @DisplayName("Should successfully process audio, upload to S3 and dispatch to Whisper")
    void shouldSuccessfullyProcessAndDispatch(@TempDir File tempDir) throws Exception {
        YoutubeChannel channel = new YoutubeChannel("UC_123", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(105);
        channel.setTimezone("America/Mexico_City");
        channel.setLanguageCode("es");

        YoutubeVideo video = new YoutubeVideo("vid_abc", channel, "Test Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);
        video.setPublishedAt(Instant.parse("2026-10-06T10:00:00Z"));

        File mockRawAudio = new File(tempDir, "full_audio.mp3");
        File chunk1 = new File(tempDir, "chunk_0000.mp3");
        File chunk2 = new File(tempDir, "chunk_0001.mp3");

        when(videoRepository.findByVideoId("vid_abc")).thenReturn(Optional.of(video));
        when(audioProcessingService.downloadAudio(anyString(), any(), anyString())).thenReturn(mockRawAudio);
        when(audioProcessingService.splitAndNormalizeAudio(any(), any())).thenReturn(List.of(chunk1, chunk2));
        when(audioProcessingService.getChunkDurationSeconds()).thenReturn(300);
        when(s3AudioUploadService.getDefaultBucket()).thenReturn("monitorlatino");
        when(s3AudioUploadService.getDefaultPrefix()).thenReturn("CHARTS");
        when(s3AudioUploadService.buildS3Key(any(), any(), any(), any())).thenReturn("CHART/YT/MILENIO/20261006/20261006_040000.mp3");
        when(s3AudioUploadService.uploadChunk(any(), any(), any())).thenReturn("s3://monitorlatino/CHART/YT/MILENIO/20261006/20261006_040000.mp3");

        when(chunkRepository.findByVideoVideoIdAndChunkIndex(anyString(), anyInt())).thenReturn(Optional.empty());
        when(chunkRepository.save(any(YoutubeAudioChunk.class))).thenAnswer(i -> i.getArgument(0));

        DownloadJobMessage message = new DownloadJobMessage("vid_abc", "UC_123", "https://youtube.com/watch?v=vid_abc");
        listener.onDownloadJob(message);

        assertEquals(VideoStatus.TRANSCRIBING, video.getStatus());
        assertEquals(2, video.getChunkCount());
        verify(chunkRepository, times(2)).save(any(YoutubeAudioChunk.class));
        verify(audioArrivalQueueService, times(2)).sendAudioArrival(any());
        verify(videoRepository, times(3)).save(video);
        // publishedAt 10:00Z -> 04:00 America/Mexico_City; second chunk +300s
        verify(s3AudioUploadService).buildS3Key(isNull(), eq("UC_123"), eq("vid_abc"), eq(java.time.LocalDateTime.of(2026, 10, 6, 4, 0, 0)));
        verify(s3AudioUploadService).buildS3Key(isNull(), eq("UC_123"), eq("vid_abc"), eq(java.time.LocalDateTime.of(2026, 10, 6, 4, 5, 0)));
    }

    @Test
    @DisplayName("Should process and upload to S3 without SQS dispatch when SQS service is absent")
    void shouldProcessAndUploadToS3WithoutSqsDispatch(@TempDir File tempDir) throws Exception {
        DownloadJobListener noSqsListener = new DownloadJobListener(
                videoRepository,
                chunkRepository,
                audioProcessingService,
                s3AudioUploadService,
                Optional.empty(),
                transcriptionApiClient,
                fixedClock,
                null,
                3
        );

        YoutubeChannel channel = new YoutubeChannel("UC_456", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(106);
        channel.setTimezone("America/Mexico_City");

        YoutubeVideo video = new YoutubeVideo("vid_nosqs", channel, "Test Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);
        video.setPublishedAt(Instant.parse("2026-10-06T10:00:00Z"));

        File mockRawAudio = new File(tempDir, "full_audio.mp3");
        File chunk1 = new File(tempDir, "chunk_0000.mp3");

        when(videoRepository.findByVideoId("vid_nosqs")).thenReturn(Optional.of(video));
        when(audioProcessingService.downloadAudio(anyString(), any(), anyString())).thenReturn(mockRawAudio);
        when(audioProcessingService.splitAndNormalizeAudio(any(), any())).thenReturn(List.of(chunk1));
        when(audioProcessingService.getChunkDurationSeconds()).thenReturn(300);
        when(s3AudioUploadService.getDefaultBucket()).thenReturn("monitorlatino");
        when(s3AudioUploadService.getDefaultPrefix()).thenReturn("CHARTS");
        when(s3AudioUploadService.buildS3Key(any(), any(), any(), any())).thenReturn("CHART/YT/MILENIO/20261006/20261006_040000.mp3");
        when(s3AudioUploadService.uploadChunk(any(), any(), any())).thenReturn("s3://monitorlatino/CHART/YT/MILENIO/20261006/20261006_040000.mp3");
        when(chunkRepository.findByVideoVideoIdAndChunkIndex(anyString(), anyInt())).thenReturn(Optional.empty());
        when(chunkRepository.save(any(YoutubeAudioChunk.class))).thenAnswer(i -> i.getArgument(0));

        DownloadJobMessage message = new DownloadJobMessage("vid_nosqs", "UC_456", "https://youtube.com/watch?v=vid_nosqs");
        noSqsListener.onDownloadJob(message);

        assertEquals(VideoStatus.TRANSCRIBING, video.getStatus());
        assertEquals(1, video.getChunkCount());
        verify(chunkRepository, times(1)).save(any(YoutubeAudioChunk.class));
        verify(audioArrivalQueueService, never()).sendAudioArrival(any());
    }

    @Test
    @DisplayName("Should upload without transcription dispatch when stationId is null")
    void shouldUploadWithoutTranscriptionWhenStationIdIsNull(@TempDir File tempDir) throws Exception {
        YoutubeChannel channel = new YoutubeChannel("UC_NO_STATION", "Test Channel", ChannelOrigin.MANUAL);
        channel.setStationId(null);

        YoutubeVideo video = new YoutubeVideo("vid_no_stn", channel, "Test Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);

        File mockRawAudio = new File(tempDir, "full_audio.mp3");
        File chunk1 = new File(tempDir, "chunk_0000.mp3");

        when(videoRepository.findByVideoId("vid_no_stn")).thenReturn(Optional.of(video));
        when(audioProcessingService.downloadAudio(anyString(), any(), anyString())).thenReturn(mockRawAudio);
        when(audioProcessingService.splitAndNormalizeAudio(any(), any())).thenReturn(List.of(chunk1));
        when(audioProcessingService.getChunkDurationSeconds()).thenReturn(300);
        when(s3AudioUploadService.getDefaultBucket()).thenReturn("monitorlatino");
        when(s3AudioUploadService.getDefaultPrefix()).thenReturn("CHARTS");
        when(s3AudioUploadService.buildS3Key(any(), any(), any(), any())).thenReturn("youtube/key.mp3");
        when(s3AudioUploadService.uploadChunk(any(), any(), any())).thenReturn("s3://bucket/key.mp3");
        when(chunkRepository.save(any(YoutubeAudioChunk.class))).thenAnswer(i -> i.getArgument(0));

        DownloadJobMessage message = new DownloadJobMessage("vid_no_stn", "UC_NO_STATION", "https://youtube.com/watch?v=vid_no_stn");
        listener.onDownloadJob(message);

        assertEquals(VideoStatus.AUDIO_READY, video.getStatus());
        verify(audioArrivalQueueService, never()).sendAudioArrival(any());
        verify(transcriptionApiClient, never()).submitTranscription(any());
    }

    @Test
    @DisplayName("Should mark video as FAILED when reaching max attempts")
    void shouldMarkVideoAsFailedOnMaxAttempts() {
        YoutubeChannel channel = new YoutubeChannel("UC_ERR", "Channel", ChannelOrigin.SUBSCRIPTION);
        YoutubeVideo video = new YoutubeVideo("vid_err", channel, "Err Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);
        video.setAttemptCount(2); // Next failure is 3 >= maxAttempts

        when(videoRepository.findByVideoId("vid_err")).thenReturn(Optional.of(video));
        when(audioProcessingService.downloadAudio(anyString(), any(), anyString()))
                .thenThrow(new AudioProcessingException("YT_DLP_DOWNLOAD_ERROR", "Private video"));

        DownloadJobMessage message = new DownloadJobMessage("vid_err", "UC_ERR", "https://youtube.com/watch?v=vid_err");

        assertThrows(RuntimeException.class, () -> listener.onDownloadJob(message));

        assertEquals(VideoStatus.FAILED, video.getStatus());
        assertEquals("YT_DLP_DOWNLOAD_ERROR", video.getLastErrorCode());
        assertEquals(3, video.getAttemptCount());
    }

    @Test
    @DisplayName("Should poll pending downloads from repository")
    void shouldPollPendingDownloadsFromRepository() {
        when(videoRepository.findByStatus(VideoStatus.QUEUED_DOWNLOAD)).thenReturn(List.of());

        listener.pollAndProcessPendingDownloads();

        verify(videoRepository).findByStatus(VideoStatus.QUEUED_DOWNLOAD);
    }
}

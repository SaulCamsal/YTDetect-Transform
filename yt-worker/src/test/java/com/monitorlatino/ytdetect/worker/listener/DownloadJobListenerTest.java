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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeSubtitleTranscript;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeSubtitleTranscriptRepository;
import com.monitorlatino.ytdetect.worker.dto.SubtitleCue;
import com.monitorlatino.ytdetect.worker.dto.SubtitleExtractionResult;
import com.monitorlatino.ytdetect.worker.service.SubtitleChunkingService;
import com.monitorlatino.ytdetect.worker.service.SubtitleExtractionService;

@ExtendWith(MockitoExtension.class)
class DownloadJobListenerTest {

    @Mock
    private YoutubeVideoRepository videoRepository;
    @Mock
    private YoutubeAudioChunkRepository chunkRepository;
    @Mock
    private YoutubeSubtitleTranscriptRepository subtitleTranscriptRepository;
    @Mock
    private AudioProcessingService audioProcessingService;
    @Mock
    private SubtitleExtractionService subtitleExtractionService;
    @Mock
    private SubtitleChunkingService subtitleChunkingService;
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
                subtitleTranscriptRepository,
                audioProcessingService,
                subtitleExtractionService,
                subtitleChunkingService,
                s3AudioUploadService,
                Optional.of(audioArrivalQueueService),
                transcriptionApiClient,
                fixedClock,
                null,
                3,
                true
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
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(i -> i.getArgument(0));
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
                subtitleTranscriptRepository,
                audioProcessingService,
                subtitleExtractionService,
                subtitleChunkingService,
                s3AudioUploadService,
                Optional.empty(),
                transcriptionApiClient,
                fixedClock,
                null,
                3,
                true
        );

        YoutubeChannel channel = new YoutubeChannel("UC_456", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(106);
        channel.setTimezone("America/Mexico_City");

        YoutubeVideo video = new YoutubeVideo("vid_nosqs", channel, "Test Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);
        video.setPublishedAt(Instant.parse("2026-10-06T10:00:00Z"));

        File mockRawAudio = new File(tempDir, "full_audio.mp3");
        File chunk1 = new File(tempDir, "chunk_0000.mp3");

        when(videoRepository.findByVideoId("vid_nosqs")).thenReturn(Optional.of(video));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(i -> i.getArgument(0));
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
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(i -> i.getArgument(0));
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
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(i -> i.getArgument(0));
        when(audioProcessingService.downloadAudio(anyString(), any(), anyString()))
                .thenThrow(new AudioProcessingException("YT_DLP_DOWNLOAD_ERROR", "Private video"));

        DownloadJobMessage message = new DownloadJobMessage("vid_err", "UC_ERR", "https://youtube.com/watch?v=vid_err");

        assertThrows(RuntimeException.class, () -> listener.onDownloadJob(message));

        assertEquals(VideoStatus.FAILED, video.getStatus());
        assertEquals("YT_DLP_DOWNLOAD_ERROR", video.getLastErrorCode());
        assertEquals(3, video.getAttemptCount());
    }

    @Test
    @DisplayName("Should keep using the instance returned by save() to avoid stale @Version")
    void shouldUseInstanceReturnedBySave(@TempDir File tempDir) throws Exception {
        YoutubeChannel channel = new YoutubeChannel("UC_MERGE", "Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(107);
        YoutubeVideo detached = new YoutubeVideo("vid_merge", channel, "Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);
        // Simulates JPA merge(): save() returns a different (managed) instance
        YoutubeVideo merged = new YoutubeVideo("vid_merge", channel, "Video", VideoStatus.DOWNLOADING, DiscoveredVia.WEBSUB);

        when(videoRepository.findByVideoId("vid_merge")).thenReturn(Optional.of(detached));
        when(videoRepository.save(same(detached))).thenReturn(merged);
        when(videoRepository.save(same(merged))).thenReturn(merged);
        when(audioProcessingService.downloadAudio(anyString(), any(), anyString())).thenReturn(new File(tempDir, "full_audio.mp3"));
        when(audioProcessingService.splitAndNormalizeAudio(any(), any())).thenReturn(List.of(new File(tempDir, "chunk_0000.mp3")));
        when(audioProcessingService.getChunkDurationSeconds()).thenReturn(300);
        when(s3AudioUploadService.buildS3Key(any(), any(), any(), any())).thenReturn("youtube/key.mp3");
        when(s3AudioUploadService.uploadChunk(any(), any(), any())).thenReturn("s3://bucket/key.mp3");
        when(chunkRepository.save(any(YoutubeAudioChunk.class))).thenAnswer(i -> i.getArgument(0));

        listener.onDownloadJob(new DownloadJobMessage("vid_merge", "UC_MERGE", "https://youtube.com/watch?v=vid_merge"));

        // YoutubeVideo.equals() compares videoId, so same() is needed to check identity
        // Only the first save uses the detached instance; the rest use the returned one
        verify(videoRepository, times(1)).save(same(detached));
        verify(videoRepository, times(2)).save(same(merged));
        assertEquals(VideoStatus.TRANSCRIBING, merged.getStatus());
        assertEquals(1, merged.getChunkCount());
    }

    @Test
    @DisplayName("Should re-queue video for retry when attempts remain")
    void shouldRequeueVideoWhenAttemptsRemain() {
        YoutubeChannel channel = new YoutubeChannel("UC_RETRY", "Channel", ChannelOrigin.SUBSCRIPTION);
        YoutubeVideo video = new YoutubeVideo("vid_retry", channel, "Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);

        when(videoRepository.findByVideoId("vid_retry")).thenReturn(Optional.of(video));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(i -> i.getArgument(0));
        when(audioProcessingService.downloadAudio(anyString(), any(), anyString()))
                .thenThrow(new AudioProcessingException("YT_DLP_DOWNLOAD_ERROR", "Network error"));

        assertThrows(RuntimeException.class,
                () -> listener.onDownloadJob(new DownloadJobMessage("vid_retry", "UC_RETRY", "https://youtube.com/watch?v=vid_retry")));

        assertEquals(VideoStatus.QUEUED_DOWNLOAD, video.getStatus());
        assertEquals(1, video.getAttemptCount());
        assertEquals("YT_DLP_DOWNLOAD_ERROR", video.getLastErrorCode());
    }

    @Test
    @DisplayName("Should poll pending downloads from repository")
    void shouldPollPendingDownloadsFromRepository() {
        when(videoRepository.findByStatus(VideoStatus.QUEUED_DOWNLOAD)).thenReturn(List.of());

        listener.pollAndProcessPendingDownloads();

        verify(videoRepository).findByStatus(VideoStatus.QUEUED_DOWNLOAD);
    }

    @Test
    @DisplayName("Should use subtitle fast-path when available and bypass audio download pipeline")
    void shouldUseSubtitleFastPathWhenAvailable(@TempDir File tempDir) throws Exception {
        YoutubeChannel channel = new YoutubeChannel("UC_FAST", "Fast Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(200);
        YoutubeVideo video = new YoutubeVideo("vid_fast", channel, "Fast Video", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);

        when(videoRepository.findByVideoId("vid_fast")).thenReturn(Optional.of(video));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(i -> i.getArgument(0));

        List<SubtitleCue> cues = List.of(new SubtitleCue(1.0, 5.0, "Texto de subtítulo"));
        SubtitleExtractionResult extractionResult = new SubtitleExtractionResult("es", cues);
        when(subtitleExtractionService.extractSubtitles(anyString(), any())).thenReturn(Optional.of(extractionResult));

        YoutubeSubtitleTranscript mockTranscript = new YoutubeSubtitleTranscript();
        mockTranscript.setChunkIndex(0);
        when(subtitleChunkingService.createChunks(any(), eq(cues), eq("es"), any())).thenReturn(List.of(mockTranscript));

        DownloadJobMessage message = new DownloadJobMessage("vid_fast", "UC_FAST", "https://youtube.com/watch?v=vid_fast");
        listener.onDownloadJob(message);

        assertEquals(VideoStatus.COMPLETED, video.getStatus());
        assertEquals(1, video.getChunkCount());
        verify(subtitleTranscriptRepository).saveAll(any());
        verify(audioProcessingService, never()).downloadAudio(anyString(), any(), anyString());
        verify(s3AudioUploadService, never()).uploadChunk(any(), any(), any());
        verify(audioArrivalQueueService, never()).sendAudioArrival(any());
        verify(transcriptionApiClient, never()).submitTranscription(any());
    }

    @Test
    @DisplayName("Fast-path should pass the same base aired time as the audio path (published_at in channel timezone)")
    void fastPathUsesChannelLocalAiredTime(@TempDir File tempDir) throws Exception {
        YoutubeChannel channel = new YoutubeChannel("UC_FAST2", "Fast Channel 2", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(201);
        channel.setTimezone("America/Mexico_City");
        YoutubeVideo video = new YoutubeVideo("vid_fast2", channel, "Fast Video 2", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);
        video.setPublishedAt(Instant.parse("2026-10-06T10:00:00Z"));   // 04:00 en CDMX

        when(videoRepository.findByVideoId("vid_fast2")).thenReturn(Optional.of(video));
        when(videoRepository.save(any(YoutubeVideo.class))).thenAnswer(i -> i.getArgument(0));
        List<SubtitleCue> cues = List.of(new SubtitleCue(1.0, 5.0, "Hola"));
        when(subtitleExtractionService.extractSubtitles(anyString(), any()))
                .thenReturn(Optional.of(new SubtitleExtractionResult("es", cues)));
        when(subtitleChunkingService.createChunks(any(), eq(cues), eq("es"), any())).thenReturn(List.of(new YoutubeSubtitleTranscript()));

        listener.onDownloadJob(new DownloadJobMessage("vid_fast2", "UC_FAST2", "https://youtube.com/watch?v=vid_fast2"));

        verify(subtitleChunkingService).createChunks(any(), eq(cues), eq("es"),
                eq(java.time.LocalDateTime.of(2026, 10, 6, 4, 0, 0)));
    }
}

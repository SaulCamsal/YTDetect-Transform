package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.StationTranscriptionItemDto;
import com.monitorlatino.ytdetect.api.client.TranscriptionClient;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionSyncServiceTest {

    @Mock
    private YoutubeAudioChunkRepository chunkRepository;
    @Mock
    private YoutubeVideoRepository videoRepository;
    @Mock
    private TranscriptionClient transcriptionClient;

    private Clock fixedClock;
    private TranscriptionSyncService service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);
        service = new TranscriptionSyncService(
                chunkRepository,
                videoRepository,
                transcriptionClient,
                fixedClock
        );
    }

    @Test
    @DisplayName("Should update chunk status and mark video COMPLETED when all chunks finish")
    void shouldUpdateChunkAndCompleteVideo() {
        YoutubeChannel channel = new YoutubeChannel("UC_1", "Chan", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(101);

        YoutubeVideo video = new YoutubeVideo("vid_1", channel, "Title", VideoStatus.TRANSCRIBING, DiscoveredVia.WEBSUB);

        UUID jobId = UUID.randomUUID();
        YoutubeAudioChunk chunk = new YoutubeAudioChunk(video, 0, BigDecimal.ZERO, LocalDateTime.of(2026, 10, 6, 10, 0), "key");
        chunk.setTranscriptionJobId(jobId);
        chunk.setTranscriptionStatus(TranscriptionStatus.QUEUED);

        when(chunkRepository.findByTranscriptionStatus(TranscriptionStatus.QUEUED)).thenReturn(List.of(chunk));
        when(chunkRepository.findByTranscriptionStatus(TranscriptionStatus.PROCESSING)).thenReturn(List.of());

        StationTranscriptionItemDto apiItem = new StationTranscriptionItemDto(jobId, "COMPLETED", "s3://audio");
        when(transcriptionClient.fetchStationTranscriptions(eq(101), any())).thenReturn(List.of(apiItem));

        when(videoRepository.findByStatus(VideoStatus.TRANSCRIBING)).thenReturn(List.of(video));
        when(chunkRepository.findByVideoVideoIdOrderByChunkIndexAsc("vid_1")).thenReturn(List.of(chunk));

        service.syncPendingTranscriptions();

        assertEquals(TranscriptionStatus.COMPLETED, chunk.getTranscriptionStatus());
        assertEquals(VideoStatus.COMPLETED, video.getStatus());
        verify(chunkRepository).save(chunk);
        verify(videoRepository).save(video);
    }
}

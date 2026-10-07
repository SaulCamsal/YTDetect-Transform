package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.TranscriptionClient;
import com.monitorlatino.ytdetect.api.client.TranscriptionJobResultDto;
import com.monitorlatino.ytdetect.api.client.TranscriptionSegmentDto;
import com.monitorlatino.ytdetect.api.dto.FullVideoTranscriptResponse;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoTranscriptAssemblyServiceTest {

    @Mock
    private YoutubeVideoRepository videoRepository;
    @Mock
    private YoutubeAudioChunkRepository chunkRepository;
    @Mock
    private TranscriptionClient transcriptionClient;

    private VideoTranscriptAssemblyService service;

    @BeforeEach
    void setUp() {
        service = new VideoTranscriptAssemblyService(videoRepository, chunkRepository, transcriptionClient);
    }

    @Test
    @DisplayName("Should assemble full video transcript with global offset calculations")
    void shouldAssembleFullVideoTranscript() {
        YoutubeChannel channel = new YoutubeChannel("UC_TEST", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        YoutubeVideo video = new YoutubeVideo("vid_assembly", channel, "Assembly Video", VideoStatus.COMPLETED, DiscoveredVia.WEBSUB);
        video.setChunkCount(2);

        UUID job1 = UUID.randomUUID();
        UUID job2 = UUID.randomUUID();

        YoutubeAudioChunk chunk1 = new YoutubeAudioChunk(video, 0, BigDecimal.ZERO, LocalDateTime.now(), "key1");
        chunk1.setTranscriptionJobId(job1);

        YoutubeAudioChunk chunk2 = new YoutubeAudioChunk(video, 1, BigDecimal.valueOf(300), LocalDateTime.now(), "key2");
        chunk2.setTranscriptionJobId(job2);

        when(videoRepository.findByVideoId("vid_assembly")).thenReturn(Optional.of(video));
        when(chunkRepository.findByVideoVideoIdOrderByChunkIndexAsc("vid_assembly")).thenReturn(List.of(chunk1, chunk2));

        TranscriptionJobResultDto res1 = new TranscriptionJobResultDto(
                job1, "COMPLETED", List.of(new TranscriptionSegmentDto(0.0, 5.0, "Hello"))
        );
        TranscriptionJobResultDto res2 = new TranscriptionJobResultDto(
                job2, "COMPLETED", List.of(new TranscriptionSegmentDto(0.0, 6.0, "World"))
        );

        when(transcriptionClient.fetchJobResult(job1)).thenReturn(Optional.of(res1));
        when(transcriptionClient.fetchJobResult(job2)).thenReturn(Optional.of(res2));

        Optional<FullVideoTranscriptResponse> resultOpt = service.assembleTranscript("vid_assembly");

        assertTrue(resultOpt.isPresent());
        FullVideoTranscriptResponse transcript = resultOpt.get();

        assertEquals("vid_assembly", transcript.videoId());
        assertEquals("Hello World", transcript.fullText());
        assertEquals(2, transcript.segments().size());

        // Chunk 0 segment
        assertEquals(0.0, transcript.segments().get(0).start());
        assertEquals(5.0, transcript.segments().get(0).end());
        assertEquals("Hello", transcript.segments().get(0).text());

        // Chunk 1 segment with global offset (+300s)
        assertEquals(300.0, transcript.segments().get(1).start());
        assertEquals(306.0, transcript.segments().get(1).end());
        assertEquals("World", transcript.segments().get(1).text());
    }
}

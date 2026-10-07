package com.monitorlatino.ytdetect.api.controller;

import com.monitorlatino.ytdetect.api.dto.AudioChunkResponse;
import com.monitorlatino.ytdetect.api.dto.VideoResponse;
import com.monitorlatino.ytdetect.api.dto.VideoTranscriptionResponse;
import com.monitorlatino.ytdetect.api.security.ApiKeyAuthFilter;
import com.monitorlatino.ytdetect.api.service.YoutubeVideoService;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.LiveStatus;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoController.class)
@Import(ApiKeyAuthFilter.class)
class VideoControllerTest {

    private static final String API_KEY = "default-secret-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YoutubeVideoService videoService;

    @MockitoBean
    private com.monitorlatino.ytdetect.api.service.VideoTranscriptAssemblyService transcriptAssemblyService;

    @Test
    @DisplayName("Should return 401 when X-API-Key is missing")
    void shouldReturn401WhenApiKeyMissing() throws Exception {
        mockMvc.perform(get("/api/v1/youtube/videos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 200 and paged videos when authorized")
    void shouldReturnPagedVideosWhenAuthorized() throws Exception {
        VideoResponse videoResponse = new VideoResponse(
                1L,
                "vid_123",
                "UC_CHAN",
                "Channel Name",
                "Video Title",
                "Description",
                Instant.now(),
                1800,
                LiveStatus.NONE,
                VideoStatus.QUEUED_DOWNLOAD,
                DiscoveredVia.WEBSUB,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                Instant.now(),
                null
        );

        when(videoService.getVideos(eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(videoResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/youtube/videos")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].videoId").value("vid_123"))
                .andExpect(jsonPath("$.content[0].status").value("QUEUED_DOWNLOAD"));
    }

    @Test
    @DisplayName("Should return 200 and video details by videoId")
    void shouldReturnVideoByVideoId() throws Exception {
        VideoResponse videoResponse = new VideoResponse(
                1L,
                "vid_456",
                "UC_CHAN",
                "Channel Name",
                "Special Video",
                "Description",
                Instant.now(),
                1200,
                LiveStatus.NONE,
                VideoStatus.DOWNLOADING,
                DiscoveredVia.POLLING,
                "bucket",
                "prefix",
                4,
                null,
                0,
                null,
                null,
                Instant.now(),
                null
        );

        when(videoService.getVideoByVideoId("vid_456")).thenReturn(Optional.of(videoResponse));

        mockMvc.perform(get("/api/v1/youtube/videos/vid_456")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value("vid_456"))
                .andExpect(jsonPath("$.title").value("Special Video"))
                .andExpect(jsonPath("$.status").value("DOWNLOADING"));
    }

    @Test
    @DisplayName("Should return 404 when videoId not found")
    void shouldReturn404WhenVideoNotFound() throws Exception {
        when(videoService.getVideoByVideoId("unknown_id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/youtube/videos/unknown_id")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 200 and chunks transcription for video")
    void shouldReturnVideoTranscription() throws Exception {
        AudioChunkResponse chunk = new AudioChunkResponse(
                10L,
                0,
                BigDecimal.ZERO,
                BigDecimal.valueOf(300),
                LocalDateTime.now(),
                "youtube/UC_CHAN/vid_789/chunk_0.mp3",
                UUID.randomUUID(),
                TranscriptionStatus.COMPLETED,
                null,
                null
        );

        VideoTranscriptionResponse transcriptionResponse = new VideoTranscriptionResponse(
                "vid_789",
                VideoStatus.COMPLETED,
                1,
                List.of(chunk)
        );

        when(videoService.getVideoTranscription("vid_789")).thenReturn(Optional.of(transcriptionResponse));

        mockMvc.perform(get("/api/v1/youtube/videos/vid_789/transcription")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value("vid_789"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.chunks[0].transcriptionStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("Should return 200 and assembled transcript for video")
    void shouldReturnAssembledTranscript() throws Exception {
        com.monitorlatino.ytdetect.api.dto.FullVideoTranscriptResponse transcript = new com.monitorlatino.ytdetect.api.dto.FullVideoTranscriptResponse(
                "vid_789",
                "Video Title",
                VideoStatus.COMPLETED,
                2,
                "Texto completo de prueba",
                List.of(new com.monitorlatino.ytdetect.api.dto.TranscriptSegmentResponse(0.0, 5.0, "Texto completo"))
        );

        when(transcriptAssemblyService.assembleTranscript("vid_789")).thenReturn(Optional.of(transcript));

        mockMvc.perform(get("/api/v1/youtube/videos/vid_789/transcript")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value("vid_789"))
                .andExpect(jsonPath("$.fullText").value("Texto completo de prueba"))
                .andExpect(jsonPath("$.segments[0].text").value("Texto completo"));
    }
}

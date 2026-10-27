package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeSubtitleTranscript;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.worker.dto.SubtitleCue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleChunkingServiceTest {

    private Clock clock;
    private SubtitleChunkingService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        service = new SubtitleChunkingService(clock, 300);
    }

    @Test
    @DisplayName("Should split cues into 300-second chunks with valid GZIP segments and relative offsets")
    void shouldSplitCuesInto300SecondChunks() throws IOException {
        YoutubeChannel channel = new YoutubeChannel("UC_TEST", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setStationId(9001);
        YoutubeVideo video = new YoutubeVideo("vid_chunk_test", channel, "Title", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);
        video.setDurationSeconds(700); // 700s = 3 chunks (0..300, 300..600, 600..700)

        List<SubtitleCue> cues = List.of(
                new SubtitleCue(10.0, 5.0, "Inicio de video"),
                new SubtitleCue(290.0, 5.0, "Casi al final del primer bloque"),
                new SubtitleCue(310.0, 4.0, "Segundo bloque"),
                new SubtitleCue(620.0, 6.0, "Tercer bloque final")
        );

        LocalDateTime base = LocalDateTime.of(2026, 10, 15, 6, 0, 0);
        List<YoutubeSubtitleTranscript> chunks = service.createChunks(video, cues, "es", base);

        assertEquals(3, chunks.size());

        // Emisora y hora al aire por bloque (lo que lee analytics.vw_contenido)
        assertEquals(9001, chunks.get(0).getStationId());
        assertEquals(base, chunks.get(0).getAiredAtLocal());
        assertEquals(base.plusSeconds(300), chunks.get(1).getAiredAtLocal());
        assertEquals(base.plusSeconds(600), chunks.get(2).getAiredAtLocal());

        // Chunk 0: [0, 300]
        YoutubeSubtitleTranscript chunk0 = chunks.get(0);
        assertEquals(0, chunk0.getChunkIndex());
        assertEquals(0, chunk0.getStartSeconds().compareTo(BigDecimal.ZERO));
        assertEquals(0, chunk0.getEndSeconds().compareTo(BigDecimal.valueOf(300)));
        assertEquals("Inicio de video Casi al final del primer bloque", chunk0.getTranscriptText());
        assertEquals(2, chunk0.getSegmentCount());
        assertNotNull(chunk0.getSegmentsGz());
        assertTrue(chunk0.getSegmentsRawBytes() > 0);

        // Decompress and verify segments in Chunk 0
        byte[] decompressed0 = service.decompressGzip(chunk0.getSegmentsGz());
        assertEquals(chunk0.getSegmentsRawBytes(), decompressed0.length);
        List<Map<String, Object>> segs0 = service.parseSegmentsJson(decompressed0);
        assertEquals(2, segs0.size());
        assertEquals(10.0, ((Number) segs0.get(0).get("start")).doubleValue());
        assertEquals(15.0, ((Number) segs0.get(0).get("end")).doubleValue());
        assertEquals("Inicio de video", segs0.get(0).get("text"));

        // Chunk 1: [300, 600]
        YoutubeSubtitleTranscript chunk1 = chunks.get(1);
        assertEquals(1, chunk1.getChunkIndex());
        assertEquals(0, chunk1.getStartSeconds().compareTo(BigDecimal.valueOf(300)));
        assertEquals(0, chunk1.getEndSeconds().compareTo(BigDecimal.valueOf(600)));
        assertEquals("Segundo bloque", chunk1.getTranscriptText());
        assertEquals(1, chunk1.getSegmentCount());

        // Check relative offset in Chunk 1: 310s global - 300s chunk start = 10.0s relative
        byte[] decompressed1 = service.decompressGzip(chunk1.getSegmentsGz());
        List<Map<String, Object>> segs1 = service.parseSegmentsJson(decompressed1);
        assertEquals(1, segs1.size());
        assertEquals(10.0, ((Number) segs1.get(0).get("start")).doubleValue());
        assertEquals(14.0, ((Number) segs1.get(0).get("end")).doubleValue());
        assertEquals("Segundo bloque", segs1.get(0).get("text"));

        // Chunk 2: [600, 700]
        YoutubeSubtitleTranscript chunk2 = chunks.get(2);
        assertEquals(2, chunk2.getChunkIndex());
        assertEquals(0, chunk2.getStartSeconds().compareTo(BigDecimal.valueOf(600)));
        assertEquals(0, chunk2.getEndSeconds().compareTo(BigDecimal.valueOf(700)));
        assertEquals("Tercer bloque final", chunk2.getTranscriptText());
        assertEquals(1, chunk2.getSegmentCount());

        // Relative offset in Chunk 2: 620s global - 600s chunk start = 20.0s relative
        byte[] decompressed2 = service.decompressGzip(chunk2.getSegmentsGz());
        List<Map<String, Object>> segs2 = service.parseSegmentsJson(decompressed2);
        assertEquals(1, segs2.size());
        assertEquals(20.0, ((Number) segs2.get(0).get("start")).doubleValue());
        assertEquals(26.0, ((Number) segs2.get(0).get("end")).doubleValue());
        assertEquals("Tercer bloque final", segs2.get(0).get("text"));
    }

    @Test
    @DisplayName("Should return empty list when cues list is empty or null")
    void shouldReturnEmptyListWhenCuesEmpty() {
        YoutubeChannel channel = new YoutubeChannel("UC_TEST", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        YoutubeVideo video = new YoutubeVideo("vid_empty", channel, "Title", VideoStatus.QUEUED_DOWNLOAD, DiscoveredVia.WEBSUB);

        assertTrue(service.createChunks(video, List.of(), "es", null).isEmpty());
        assertTrue(service.createChunks(video, null, "es", null).isEmpty());
    }
}

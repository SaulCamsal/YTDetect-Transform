package com.monitorlatino.ytdetect.worker.service;

import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class S3AudioUploadServiceTest {

    private final S3AudioUploadService service =
            new S3AudioUploadService(mock(S3Template.class), "monitor-youtube-audio", "youtube");

    @Test
    void buildS3KeyUsesLocalAiredTimeAsFileName() {
        String key = service.buildS3Key("UC123", "abc", LocalDateTime.of(2026, 9, 1, 12, 0, 0));
        assertEquals("youtube/UC123/2026/09/abc/20260901_120000.mp3", key);
    }

    @Test
    void buildS3KeyWithDedicatedS3Folder() {
        String key = service.buildS3Key("/CHART/YT/MILENIO", "UC123", "abc", LocalDateTime.of(2026, 10, 14, 12, 0, 0));
        assertEquals("CHART/YT/MILENIO/20261014/20261014_120000.mp3", key);
    }

    @Test
    void buildS3KeyWithNullOrBlankFolderFallsBackToDefault() {
        String keyNull = service.buildS3Key(null, "UC123", "abc", LocalDateTime.of(2026, 9, 1, 12, 0, 0));
        assertEquals("youtube/UC123/2026/09/abc/20260901_120000.mp3", keyNull);

        String keyBlank = service.buildS3Key("   ", "UC123", "abc", LocalDateTime.of(2026, 9, 1, 12, 0, 0));
        assertEquals("youtube/UC123/2026/09/abc/20260901_120000.mp3", keyBlank);
    }

    @Test
    void buildS3KeyRequiresAiredTime() {
        assertThrows(NullPointerException.class, () -> service.buildS3Key("UC123", "abc", null));
        assertThrows(NullPointerException.class, () -> service.buildS3Key("/CHART/YT/MILENIO", "UC123", "abc", null));
    }
}

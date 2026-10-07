package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.worker.cli.CliProcessRunner;
import com.monitorlatino.ytdetect.worker.cli.ProcessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioProcessingServiceTest {

    @Mock
    private CliProcessRunner processRunner;

    private AudioProcessingService service;

    @BeforeEach
    void setUp() {
        service = new AudioProcessingService(processRunner, "yt-dlp", "ffmpeg", 300);
    }

    @Test
    @DisplayName("Should download audio successfully when CLI returns 0")
    void shouldDownloadAudioSuccessfully(@TempDir File tempDir) throws Exception {
        String outputFileName = "audio.mp3";
        File fakeAudioFile = new File(tempDir, outputFileName);
        assertTrue(fakeAudioFile.createNewFile());

        when(processRunner.runCommand(any(), any()))
                .thenReturn(new ProcessResult(0, "Downloaded", ""));

        File result = service.downloadAudio("https://youtube.com/watch?v=123", tempDir, outputFileName);

        assertNotNull(result);
        assertTrue(result.exists());
        assertEquals(outputFileName, result.getName());
    }

    @Test
    @DisplayName("Should throw AudioProcessingException when yt-dlp fails")
    void shouldThrowExceptionWhenYtDlpFails(@TempDir File tempDir) throws Exception {
        when(processRunner.runCommand(any(), any()))
                .thenReturn(new ProcessResult(1, "", "ERROR: Video unavailable"));

        AudioProcessingException ex = assertThrows(AudioProcessingException.class, () ->
                service.downloadAudio("https://youtube.com/watch?v=error", tempDir, "audio.mp3")
        );

        assertEquals("YT_DLP_DOWNLOAD_ERROR", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Video unavailable"));
    }

    @Test
    @DisplayName("Should split audio into chunks and return sorted list")
    void shouldSplitAudioIntoChunks(@TempDir File tempDir) throws Exception {
        File sourceAudio = new File(tempDir, "source.mp3");
        assertTrue(sourceAudio.createNewFile());

        File chunksDir = new File(tempDir, "chunks");
        assertTrue(chunksDir.mkdirs());

        // Create mock chunks as would be produced by ffmpeg
        File chunk1 = new File(chunksDir, "chunk_0000.mp3");
        File chunk2 = new File(chunksDir, "chunk_0001.mp3");
        assertTrue(chunk1.createNewFile());
        assertTrue(chunk2.createNewFile());

        when(processRunner.runCommand(any(), any()))
                .thenReturn(new ProcessResult(0, "ffmpeg output", ""));

        List<File> chunks = service.splitAndNormalizeAudio(sourceAudio, chunksDir);

        assertEquals(2, chunks.size());
        assertEquals("chunk_0000.mp3", chunks.get(0).getName());
        assertEquals("chunk_0001.mp3", chunks.get(1).getName());
    }

    @Test
    @DisplayName("Should throw AudioProcessingException when ffmpeg fails")
    void shouldThrowExceptionWhenFfmpegFails(@TempDir File tempDir) throws Exception {
        File sourceAudio = new File(tempDir, "source.mp3");
        assertTrue(sourceAudio.createNewFile());
        File chunksDir = new File(tempDir, "chunks");

        when(processRunner.runCommand(any(), any()))
                .thenReturn(new ProcessResult(1, "", "ffmpeg error: invalid file"));

        AudioProcessingException ex = assertThrows(AudioProcessingException.class, () ->
                service.splitAndNormalizeAudio(sourceAudio, chunksDir)
        );

        assertEquals("FFMPEG_SPLIT_ERROR", ex.getErrorCode());
    }
}

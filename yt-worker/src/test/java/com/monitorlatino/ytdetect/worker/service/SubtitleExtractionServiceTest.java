package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.worker.cli.CliProcessRunner;
import com.monitorlatino.ytdetect.worker.cli.ProcessResult;
import com.monitorlatino.ytdetect.worker.dto.SubtitleCue;
import com.monitorlatino.ytdetect.worker.dto.SubtitleExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubtitleExtractionServiceTest {

    @Mock
    private CliProcessRunner processRunner;

    private SubtitleExtractionService service;

    @BeforeEach
    void setUp() {
        service = new SubtitleExtractionService(
                processRunner,
                "yt-dlp",
                "es,es-419,es-ES,es-orig,en"
        );
    }

    @Test
    @DisplayName("Should extract subtitles from json3 file successfully")
    void shouldExtractSubtitlesFromJson3FileSuccessfully(@TempDir File tempDir) throws Exception {
        String json3Content = """
                {
                  "events": [
                    {
                      "tStartMs": 1000,
                      "dDurationMs": 2500,
                      "segs": [
                        { "utf8": "Hola" },
                        { "utf8": " mundo" }
                      ]
                    },
                    {
                      "tStartMs": 4000,
                      "dDurationMs": 1500,
                      "segs": [
                        { "utf8": "bienvenidos" }
                      ]
                    }
                  ]
                }
                """;

        when(processRunner.runCommand(any(), any())).thenAnswer(invocation -> {
            File workDir = invocation.getArgument(1);
            File subFile = new File(workDir, "subs.es.json3");
            Files.writeString(subFile.toPath(), json3Content);
            return new ProcessResult(0, "Subtitles downloaded", "");
        });

        Optional<SubtitleExtractionResult> resultOpt = service.extractSubtitles("https://youtube.com/watch?v=test12345", tempDir);

        assertTrue(resultOpt.isPresent());
        SubtitleExtractionResult result = resultOpt.get();
        assertEquals("es", result.language());
        assertEquals(2, result.cues().size());

        SubtitleCue cue1 = result.cues().get(0);
        assertEquals(1.0, cue1.startSeconds());
        assertEquals(2.5, cue1.durationSeconds());
        assertEquals(3.5, cue1.endSeconds());
        assertEquals("Hola mundo", cue1.text());

        SubtitleCue cue2 = result.cues().get(1);
        assertEquals(4.0, cue2.startSeconds());
        assertEquals(1.5, cue2.durationSeconds());
        assertEquals(5.5, cue2.endSeconds());
        assertEquals("bienvenidos", cue2.text());
    }

    @Test
    @DisplayName("Should return empty when no subtitle file is generated")
    void shouldReturnEmptyWhenNoSubtitleFileIsGenerated(@TempDir File tempDir) throws Exception {
        when(processRunner.runCommand(any(), any()))
                .thenReturn(new ProcessResult(0, "No subtitles", ""));

        Optional<SubtitleExtractionResult> resultOpt = service.extractSubtitles("https://youtube.com/watch?v=nosubs", tempDir);

        assertFalse(resultOpt.isPresent());
    }

    @Test
    @DisplayName("Should return empty when subtitle file contains no valid cues")
    void shouldReturnEmptyWhenSubtitleFileContainsNoValidCues(@TempDir File tempDir) throws Exception {
        String emptyJson = """
                {
                  "events": [
                    {
                      "tStartMs": 0,
                      "dDurationMs": 1000,
                      "segs": [
                        { "utf8": "\\n" }
                      ]
                    }
                  ]
                }
                """;

        when(processRunner.runCommand(any(), any())).thenAnswer(invocation -> {
            File workDir = invocation.getArgument(1);
            File subFile = new File(workDir, "subs.es.json3");
            Files.writeString(subFile.toPath(), emptyJson);
            return new ProcessResult(0, "Downloaded", "");
        });

        Optional<SubtitleExtractionResult> resultOpt = service.extractSubtitles("https://youtube.com/watch?v=empty", tempDir);

        assertFalse(resultOpt.isPresent());
    }

    @Test
    @DisplayName("Should prioritize Spanish subtitles when both Spanish and English exist")
    void shouldPrioritizeSpanishSubtitles(@TempDir File tempDir) throws Exception {
        String esContent = """
                {
                  "events": [
                    { "tStartMs": 1000, "dDurationMs": 2000, "segs": [{ "utf8": "Español" }] }
                  ]
                }
                """;
        String enContent = """
                {
                  "events": [
                    { "tStartMs": 1000, "dDurationMs": 2000, "segs": [{ "utf8": "English" }] }
                  ]
                }
                """;

        when(processRunner.runCommand(any(), any())).thenAnswer(invocation -> {
            File workDir = invocation.getArgument(1);
            Files.writeString(new File(workDir, "subs.en.json3").toPath(), enContent);
            Files.writeString(new File(workDir, "subs.es.json3").toPath(), esContent);
            return new ProcessResult(0, "Downloaded both", "");
        });

        Optional<SubtitleExtractionResult> resultOpt = service.extractSubtitles("https://youtube.com/watch?v=multilang", tempDir);

        assertTrue(resultOpt.isPresent());
        assertEquals("es", resultOpt.get().language());
        assertEquals("Español", resultOpt.get().cues().get(0).text());
    }
}

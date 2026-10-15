package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.worker.cli.CliProcessRunner;
import com.monitorlatino.ytdetect.worker.cli.ProcessResult;
import com.monitorlatino.ytdetect.worker.dto.SubtitleCue;
import com.monitorlatino.ytdetect.worker.dto.SubtitleExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.JsonParser;
import software.amazon.awssdk.thirdparty.jackson.core.JsonToken;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class SubtitleExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SubtitleExtractionService.class);

    private final CliProcessRunner processRunner;
    private final String ytDlpPath;
    private final String subtitleLanguages;
    private final JsonFactory jsonFactory = new JsonFactory();

    public SubtitleExtractionService(
            CliProcessRunner processRunner,
            @Value("${yt.worker.yt-dlp-path:yt-dlp}") String ytDlpPath,
            @Value("${yt.worker.subtitles.languages:es,es-419,es-ES,es-orig,en}") String subtitleLanguages) {
        this.processRunner = processRunner;
        this.ytDlpPath = ytDlpPath;
        this.subtitleLanguages = subtitleLanguages;
    }

    public Optional<SubtitleExtractionResult> extractSubtitles(String youtubeUrl, File workDir) {
        log.info("Attempting subtitle extraction for URL: {} with languages: {}", youtubeUrl, subtitleLanguages);

        List<String> command = List.of(
                ytDlpPath,
                "--write-auto-sub",
                "--write-sub",
                "--sub-lang", subtitleLanguages,
                "--sub-format", "json3",
                "--skip-download",
                "--no-playlist",
                "-o", "subs.%(ext)s",
                youtubeUrl
        );

        try {
            ProcessResult result = processRunner.runCommand(command, workDir);
            log.debug("yt-dlp subtitle extraction completed with exit code: {}", result.exitCode());

            File[] jsonFiles = workDir.listFiles((dir, name) -> name.startsWith("subs") && name.endsWith(".json3"));
            if (jsonFiles == null || jsonFiles.length == 0) {
                log.info("No subtitles found for URL: {}", youtubeUrl);
                return Optional.empty();
            }

            // Prioritize Spanish subtitles if multiple files exist
            Arrays.sort(jsonFiles, (f1, f2) -> {
                boolean f1Es = f1.getName().contains(".es");
                boolean f2Es = f2.getName().contains(".es");
                if (f1Es && !f2Es) return -1;
                if (!f1Es && f2Es) return 1;
                return f1.getName().compareTo(f2.getName());
            });

            File chosenSubFile = jsonFiles[0];
            String detectedLanguage = extractLanguageFromFilename(chosenSubFile.getName());
            log.info("Using subtitle file: {} (detected language: {})", chosenSubFile.getName(), detectedLanguage);

            List<SubtitleCue> cues = parseJson3Subtitles(chosenSubFile);
            if (cues.isEmpty()) {
                log.warn("Subtitle file {} contained no parseable cues", chosenSubFile.getName());
                return Optional.empty();
            }

            log.info("Extracted {} subtitle cues for video URL: {}", cues.size(), youtubeUrl);
            return Optional.of(new SubtitleExtractionResult(detectedLanguage, cues));

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Subtitle extraction encountered an error for URL {}: {}. Will fallback to audio pipeline.",
                    youtubeUrl, e.getMessage());
            return Optional.empty();
        }
    }

    public List<SubtitleCue> parseJson3Subtitles(File json3File) throws IOException {
        List<SubtitleCue> cues = new ArrayList<>();
        try (JsonParser parser = jsonFactory.createParser(json3File)) {
            while (parser.nextToken() != null) {
                if ("events".equals(parser.currentName()) && parser.currentToken() == JsonToken.START_ARRAY) {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if (parser.currentToken() == JsonToken.START_OBJECT) {
                            parseEvent(parser, cues);
                        }
                    }
                }
            }
        }
        return cues;
    }

    private void parseEvent(JsonParser parser, List<SubtitleCue> cues) throws IOException {
        long tStartMs = -1;
        long dDurationMs = 0;
        StringBuilder textBuilder = new StringBuilder();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            if ("tStartMs".equals(fieldName)) {
                tStartMs = parser.getLongValue();
            } else if ("dDurationMs".equals(fieldName)) {
                dDurationMs = parser.getLongValue();
            } else if ("segs".equals(fieldName) && parser.currentToken() == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() == JsonToken.START_OBJECT) {
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String segField = parser.currentName();
                            parser.nextToken();
                            if ("utf8".equals(segField)) {
                                textBuilder.append(parser.getText());
                            } else {
                                parser.skipChildren();
                            }
                        }
                    }
                }
            } else {
                parser.skipChildren();
            }
        }

        String text = textBuilder.toString().replace("\n", " ").trim();
        if (tStartMs >= 0 && !text.isEmpty()) {
            cues.add(new SubtitleCue(tStartMs / 1000.0, dDurationMs / 1000.0, text));
        }
    }

    private String extractLanguageFromFilename(String filename) {
        String[] parts = filename.split("\\.");
        if (parts.length >= 3) {
            return parts[1];
        }
        return "es";
    }
}

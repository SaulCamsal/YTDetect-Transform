package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.worker.cli.CliProcessRunner;
import com.monitorlatino.ytdetect.worker.cli.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class AudioProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);

    private final CliProcessRunner processRunner;
    private final String ytDlpPath;
    private final String ffmpegPath;
    private final int chunkDurationSeconds;

    public AudioProcessingService(
            CliProcessRunner processRunner,
            @Value("${yt.worker.yt-dlp-path:yt-dlp}") String ytDlpPath,
            @Value("${yt.worker.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${yt.worker.chunk-duration-seconds:300}") int chunkDurationSeconds) {
        this.processRunner = processRunner;
        this.ytDlpPath = ytDlpPath;
        this.ffmpegPath = ffmpegPath;
        this.chunkDurationSeconds = chunkDurationSeconds;
    }

    public File downloadAudio(String youtubeUrl, File workDir, String outputFileName) {
        log.info("Downloading audio for URL: {} into {}", youtubeUrl, workDir.getAbsolutePath());
        List<String> command = List.of(
                ytDlpPath,
                "-f", "bestaudio",
                "-x",
                "--audio-format", "mp3",
                "--no-playlist",
                "-o", outputFileName,
                youtubeUrl
        );

        try {
            ProcessResult result = processRunner.runCommand(command, workDir);
            if (!result.isSuccess()) {
                log.error("yt-dlp command failed with code {}: {}", result.exitCode(), result.errorOutput());
                throw new AudioProcessingException("YT_DLP_DOWNLOAD_ERROR", "yt-dlp failed: " + result.errorOutput());
            }

            File audioFile = new File(workDir, outputFileName);
            if (!audioFile.exists()) {
                throw new AudioProcessingException("AUDIO_FILE_NOT_FOUND", "Downloaded audio file does not exist: " + audioFile.getAbsolutePath());
            }

            log.info("Audio downloaded successfully: {} (size: {} bytes)", audioFile.getName(), audioFile.length());
            return audioFile;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioProcessingException("YT_DLP_EXECUTION_ERROR", "Error executing yt-dlp: " + e.getMessage(), e);
        }
    }

    public List<File> splitAndNormalizeAudio(File sourceAudioFile, File chunksDir) {
        log.info("Splitting and normalizing audio file {} into chunks of {}s in {}",
                sourceAudioFile.getName(), chunkDurationSeconds, chunksDir.getAbsolutePath());

        if (!chunksDir.exists() && !chunksDir.mkdirs()) {
            throw new AudioProcessingException("DIR_CREATE_ERROR", "Failed to create chunks directory: " + chunksDir.getAbsolutePath());
        }

        List<String> command = List.of(
                ffmpegPath,
                "-y",
                "-i", sourceAudioFile.getAbsolutePath(),
                "-f", "segment",
                "-segment_time", String.valueOf(chunkDurationSeconds),
                "-c:a", "libmp3lame",
                "-ac", "1",
                "-ar", "16000",
                "-b:a", "64k",
                "-reset_timestamps", "1",
                "chunk_%04d.mp3"
        );

        try {
            ProcessResult result = processRunner.runCommand(command, chunksDir);
            if (!result.isSuccess()) {
                log.error("ffmpeg command failed with code {}: {}", result.exitCode(), result.errorOutput());
                throw new AudioProcessingException("FFMPEG_SPLIT_ERROR", "ffmpeg splitting failed: " + result.errorOutput());
            }

            File[] chunkFiles = chunksDir.listFiles((dir, name) -> name.startsWith("chunk_") && name.endsWith(".mp3"));
            if (chunkFiles == null || chunkFiles.length == 0) {
                throw new AudioProcessingException("NO_CHUNKS_GENERATED", "ffmpeg completed but no chunk files were generated");
            }

            List<File> sortedChunks = Arrays.asList(chunkFiles);
            sortedChunks.sort(Comparator.comparing(File::getName));
            log.info("Successfully generated {} audio chunks", sortedChunks.size());
            return sortedChunks;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioProcessingException("FFMPEG_EXECUTION_ERROR", "Error executing ffmpeg: " + e.getMessage(), e);
        }
    }

    public int getChunkDurationSeconds() {
        return chunkDurationSeconds;
    }
}

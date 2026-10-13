package com.monitorlatino.ytdetect.worker.service;

import io.awspring.cloud.s3.S3Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
public class S3AudioUploadService {

    private static final Logger log = LoggerFactory.getLogger(S3AudioUploadService.class);
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final S3Template s3Template;
    private final String defaultBucket;
    private final String defaultPrefix;

    public S3AudioUploadService(
            S3Template s3Template,
            @Value("${yt.s3.bucket:monitor-youtube-audio}") String defaultBucket,
            @Value("${yt.s3.prefix:youtube}") String defaultPrefix) {
        this.s3Template = s3Template;
        this.defaultBucket = (defaultBucket != null) ? defaultBucket.replaceAll("/+$", "") : "monitor-youtube-audio";
        this.defaultPrefix = defaultPrefix;
    }

    /**
     * Builds S3 key using the station's dedicated s3Folder (e.g., /CHART/YT/MILENIO) if available:
     * {cleanFolder}/{yyyyMMdd}/{yyyyMMdd_HHmmss}.mp3
     * If s3Folder is absent, falls back to the default YouTube format:
     * {defaultPrefix}/{channelId}/{yyyy}/{MM}/{videoId}/{yyyyMMdd_HHmmss}.mp3
     */
    public String buildS3Key(String s3Folder, String channelId, String videoId, LocalDateTime chunkAiredAt) {
        Objects.requireNonNull(chunkAiredAt, "chunkAiredAt is required");
        String fileName = FILE_NAME_FORMATTER.format(chunkAiredAt) + ".mp3";

        if (s3Folder != null && !s3Folder.isBlank()) {
            String cleanFolder = s3Folder.replaceAll("^/+", "").replaceAll("/+$", "");
            String day = DAY_FORMATTER.format(chunkAiredAt);
            return String.format("%s/%s/%s", cleanFolder, day, fileName);
        }

        return buildS3Key(channelId, videoId, chunkAiredAt);
    }

    /**
     * Builds {prefix}/{channelId}/{yyyy}/{MM}/{videoId}/{yyyyMMdd_HHmmss}.mp3 using the chunk's
     * local aired time (channel timezone), as expected by the transcription API (C5).
     */
    public String buildS3Key(String channelId, String videoId, LocalDateTime chunkAiredAt) {
        Objects.requireNonNull(chunkAiredAt, "chunkAiredAt is required");
        String year = YEAR_FORMATTER.format(chunkAiredAt);
        String month = MONTH_FORMATTER.format(chunkAiredAt);
        String fileName = FILE_NAME_FORMATTER.format(chunkAiredAt) + ".mp3";
        return String.format("%s/%s/%s/%s/%s/%s", defaultPrefix, channelId, year, month, videoId, fileName);
    }

    public String uploadChunk(String bucket, String s3Key, File chunkFile) throws IOException {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucket;
        targetBucket = targetBucket.replaceAll("/+$", "");
        log.info("Uploading chunk {} to s3://{}/{}", chunkFile.getName(), targetBucket, s3Key);

        try (InputStream inputStream = new FileInputStream(chunkFile)) {
            s3Template.upload(targetBucket, s3Key, inputStream);
        }

        return "s3://" + targetBucket + "/" + s3Key;
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

    public String getDefaultPrefix() {
        return defaultPrefix;
    }
}

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class S3AudioUploadService {

    private static final Logger log = LoggerFactory.getLogger(S3AudioUploadService.class);
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);

    private final S3Template s3Template;
    private final String defaultBucket;
    private final String defaultPrefix;

    public S3AudioUploadService(
            S3Template s3Template,
            @Value("${yt.s3.bucket:monitor-youtube-audio}") String defaultBucket,
            @Value("${yt.s3.prefix:youtube}") String defaultPrefix) {
        this.s3Template = s3Template;
        this.defaultBucket = defaultBucket;
        this.defaultPrefix = defaultPrefix;
    }

    public String buildS3Key(String channelId, String videoId, Instant timestamp, int chunkIndex) {
        Instant date = timestamp != null ? timestamp : Instant.now();
        String year = YEAR_FORMATTER.format(date);
        String month = MONTH_FORMATTER.format(date);
        return String.format("%s/%s/%s/%s/%s/chunk_%04d.mp3", defaultPrefix, channelId, year, month, videoId, chunkIndex);
    }

    public String uploadChunk(String bucket, String s3Key, File chunkFile) throws IOException {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucket;
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

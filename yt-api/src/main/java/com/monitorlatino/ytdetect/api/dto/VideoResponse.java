package com.monitorlatino.ytdetect.api.dto;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.LiveStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public record VideoResponse(
        Long id,
        String videoId,
        String channelId,
        String channelName,
        String title,
        String description,
        Instant publishedAt,
        Integer durationSeconds,
        LiveStatus liveStatus,
        VideoStatus status,
        DiscoveredVia discoveredVia,
        String s3Bucket,
        String s3Prefix,
        Integer chunkCount,
        LocalDateTime virtualStart,
        int attemptCount,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static VideoResponse from(YoutubeVideo v) {
        return new VideoResponse(
                v.getId(),
                v.getVideoId(),
                v.getChannel() != null ? v.getChannel().getChannelId() : null,
                v.getChannel() != null ? v.getChannel().getChannelName() : null,
                v.getTitle(),
                v.getDescription(),
                v.getPublishedAt(),
                v.getDurationSeconds(),
                v.getLiveStatus(),
                v.getStatus(),
                v.getDiscoveredVia(),
                v.getS3Bucket(),
                v.getS3Prefix(),
                v.getChunkCount(),
                v.getVirtualStart(),
                v.getAttemptCount(),
                v.getLastErrorCode(),
                v.getLastErrorMessage(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}

package com.monitorlatino.ytdetect.api.dto;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.WebSubStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public record ChannelResponse(
        Long id,
        String channelId,
        String channelName,
        String uploadsPlaylistId,
        Integer stationId,
        String timezone,
        String languageCode,
        boolean enabled,
        ChannelOrigin origin,
        LocalDateTime timelineCursor,
        WebSubStatus websubStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChannelResponse fromEntity(YoutubeChannel channel) {
        return new ChannelResponse(
                channel.getId(),
                channel.getChannelId(),
                channel.getChannelName(),
                channel.getUploadsPlaylistId(),
                channel.getStationId(),
                channel.getTimezone(),
                channel.getLanguageCode(),
                channel.isEnabled(),
                channel.getOrigin(),
                channel.getTimelineCursor(),
                channel.getWebsubStatus(),
                channel.getCreatedAt(),
                channel.getUpdatedAt()
        );
    }
}

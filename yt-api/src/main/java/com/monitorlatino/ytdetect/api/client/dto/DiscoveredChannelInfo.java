package com.monitorlatino.ytdetect.api.client.dto;

public record DiscoveredChannelInfo(
        String channelId,
        String title,
        String uploadsPlaylistId
) {
}

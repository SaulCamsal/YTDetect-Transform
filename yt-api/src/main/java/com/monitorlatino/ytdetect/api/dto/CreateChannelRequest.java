package com.monitorlatino.ytdetect.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChannelRequest(
        @NotBlank(message = "channelId is required")
        String channelId,
        String channelName,
        Integer stationId,
        String timezone,
        String languageCode
) {
}

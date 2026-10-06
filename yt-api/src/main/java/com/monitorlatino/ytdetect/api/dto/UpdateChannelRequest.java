package com.monitorlatino.ytdetect.api.dto;

public record UpdateChannelRequest(
        Boolean enabled,
        Integer stationId,
        String timezone,
        String languageCode
) {
}

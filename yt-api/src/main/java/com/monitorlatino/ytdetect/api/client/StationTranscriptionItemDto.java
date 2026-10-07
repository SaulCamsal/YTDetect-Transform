package com.monitorlatino.ytdetect.api.client;

import java.util.UUID;

public record StationTranscriptionItemDto(
        UUID jobId,
        String status,
        String audioUrl
) {
}

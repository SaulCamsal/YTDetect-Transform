package com.monitorlatino.ytdetect.api.client;

public record TranscriptionSegmentDto(
        Double start,
        Double end,
        String text
) {
}

package com.monitorlatino.ytdetect.api.dto;

public record TranscriptSegmentResponse(
        Double start,
        Double end,
        String text
) {
}

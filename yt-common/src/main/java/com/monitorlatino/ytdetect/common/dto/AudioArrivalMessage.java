package com.monitorlatino.ytdetect.common.dto;

import java.util.UUID;

public record AudioArrivalMessage(
        UUID jobId,
        Integer stationId,
        String audioUrl,
        String fileName,
        String airedAtLocal,
        String timezone,
        String language,
        Boolean vad,
        String sourceSystem
) {
    public static AudioArrivalMessage of(
            UUID jobId,
            Integer stationId,
            String audioUrl,
            String fileName,
            String airedAtLocal,
            String timezone,
            String language
    ) {
        return new AudioArrivalMessage(
                jobId,
                stationId,
                audioUrl,
                fileName,
                airedAtLocal,
                (timezone != null && !timezone.isBlank()) ? timezone : "America/Mexico_City",
                (language != null && !language.isBlank()) ? language : "es",
                true,
                "youtube-ingest"
        );
    }
}

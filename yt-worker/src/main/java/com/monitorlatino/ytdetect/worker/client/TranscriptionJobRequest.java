package com.monitorlatino.ytdetect.worker.client;

public record TranscriptionJobRequest(
        Integer stationId,
        String audioUrl,
        String timezone,
        String language,
        boolean vad,
        String sourceSystem
) {
    public static TranscriptionJobRequest of(Integer stationId, String audioUrl, String timezone, String language) {
        return new TranscriptionJobRequest(
                stationId,
                audioUrl,
                (timezone != null && !timezone.isBlank()) ? timezone : "America/Mexico_City",
                (language != null && !language.isBlank()) ? language : "es",
                true,
                "youtube-ingest"
        );
    }
}

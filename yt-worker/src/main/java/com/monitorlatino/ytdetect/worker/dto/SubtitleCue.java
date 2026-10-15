package com.monitorlatino.ytdetect.worker.dto;

public record SubtitleCue(
        double startSeconds,
        double durationSeconds,
        String text
) {
    public double endSeconds() {
        return startSeconds + durationSeconds;
    }
}

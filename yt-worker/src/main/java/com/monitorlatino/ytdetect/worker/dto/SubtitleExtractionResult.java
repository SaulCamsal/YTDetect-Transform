package com.monitorlatino.ytdetect.worker.dto;

import java.util.List;

public record SubtitleExtractionResult(
        String language,
        List<SubtitleCue> cues
) {
}

package com.monitorlatino.ytdetect.api.dto;

import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;

import java.util.List;

public record FullVideoTranscriptResponse(
        String videoId,
        String title,
        VideoStatus status,
        Integer chunkCount,
        String fullText,
        List<TranscriptSegmentResponse> segments
) {
}

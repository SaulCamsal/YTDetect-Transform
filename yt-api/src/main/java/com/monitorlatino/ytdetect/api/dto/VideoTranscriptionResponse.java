package com.monitorlatino.ytdetect.api.dto;

import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;

import java.util.List;

public record VideoTranscriptionResponse(
        String videoId,
        VideoStatus status,
        Integer chunkCount,
        List<AudioChunkResponse> chunks
) {
}

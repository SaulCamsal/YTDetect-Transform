package com.monitorlatino.ytdetect.api.dto;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AudioChunkResponse(
        Long id,
        Integer chunkIndex,
        BigDecimal offsetSeconds,
        BigDecimal durationSeconds,
        LocalDateTime airedAtLocal,
        String s3Key,
        UUID transcriptionJobId,
        TranscriptionStatus transcriptionStatus,
        String errorCode,
        String errorMessage
) {
    public static AudioChunkResponse from(YoutubeAudioChunk c) {
        return new AudioChunkResponse(
                c.getId(),
                c.getChunkIndex(),
                c.getOffsetSeconds(),
                c.getDurationSeconds(),
                c.getAiredAtLocal(),
                c.getS3Key(),
                c.getTranscriptionJobId(),
                c.getTranscriptionStatus(),
                c.getErrorCode(),
                c.getErrorMessage()
        );
    }
}

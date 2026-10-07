package com.monitorlatino.ytdetect.api.client;

import java.util.List;
import java.util.UUID;

public record TranscriptionJobResultDto(
        UUID jobId,
        String status,
        List<TranscriptionSegmentDto> segments
) {
}

package com.monitorlatino.ytdetect.worker.client;

import java.util.UUID;

public record TranscriptionJobResponse(
        UUID jobId,
        boolean duplicate
) {
}

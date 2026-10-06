package com.monitorlatino.ytdetect.api.dto;

public record SyncResultResponse(
        String status,
        int discoveredCount,
        int newlyAddedCount,
        int updatedCount,
        String message
) {
}

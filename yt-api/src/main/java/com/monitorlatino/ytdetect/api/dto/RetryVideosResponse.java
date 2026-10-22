package com.monitorlatino.ytdetect.api.dto;

import java.util.List;

public record RetryVideosResponse(
        int retriedCount,
        List<String> videoIds
) {
}

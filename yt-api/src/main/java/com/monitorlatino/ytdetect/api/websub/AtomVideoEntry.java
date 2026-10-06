package com.monitorlatino.ytdetect.api.websub;

import java.time.Instant;

public record AtomVideoEntry(
        String videoId,
        String channelId,
        String title,
        Instant publishedAt
) {
}

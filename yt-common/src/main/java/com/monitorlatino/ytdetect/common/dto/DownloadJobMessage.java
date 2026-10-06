package com.monitorlatino.ytdetect.common.dto;

public record DownloadJobMessage(
        String videoId,
        String channelId,
        String youtubeUrl
) {
    public static DownloadJobMessage of(String videoId, String channelId) {
        return new DownloadJobMessage(videoId, channelId, "https://www.youtube.com/watch?v=" + videoId);
    }
}

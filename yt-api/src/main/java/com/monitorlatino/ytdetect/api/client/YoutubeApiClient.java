package com.monitorlatino.ytdetect.api.client;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Subscription;
import com.google.api.services.youtube.model.SubscriptionListResponse;
import com.monitorlatino.ytdetect.api.client.dto.DiscoveredChannelInfo;
import com.monitorlatino.ytdetect.api.config.YoutubeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class YoutubeApiClient {

    private static final Logger log = LoggerFactory.getLogger(YoutubeApiClient.class);

    private final YouTube youTube;
    private final YoutubeConfig youtubeConfig;

    public YoutubeApiClient(YouTube youTube, YoutubeConfig youtubeConfig) {
        this.youTube = youTube;
        this.youtubeConfig = youtubeConfig;
    }

    public List<DiscoveredChannelInfo> fetchAllSubscribedChannels() throws IOException {
        log.info("Fetching subscribed channels from YouTube Data API (subscriptions.list?mine=true)");
        List<String> channelIds = new ArrayList<>();
        Map<String, String> channelTitles = new HashMap<>();

        String nextPageToken = null;
        do {
            YouTube.Subscriptions.List request = youTube.subscriptions()
                    .list(List.of("snippet"))
                    .setMine(true)
                    .setMaxResults(50L);

            if (nextPageToken != null) {
                request.setPageToken(nextPageToken);
            }

            SubscriptionListResponse response = request.execute();
            if (response.getItems() != null) {
                for (Subscription item : response.getItems()) {
                    if (item.getSnippet() != null && item.getSnippet().getResourceId() != null) {
                        String channelId = item.getSnippet().getResourceId().getChannelId();
                        String title = item.getSnippet().getTitle();
                        if (channelId != null && !channelId.isBlank()) {
                            channelIds.add(channelId);
                            channelTitles.put(channelId, title);
                        }
                    }
                }
            }
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null);

        log.info("Found {} subscribed channels from YouTube", channelIds.size());
        if (channelIds.isEmpty()) {
            return Collections.emptyList();
        }

        return enrichChannelsWithUploadPlaylist(channelIds, channelTitles);
    }

    public DiscoveredChannelInfo fetchChannelInfo(String channelId) throws IOException {
        List<DiscoveredChannelInfo> list = enrichChannelsWithUploadPlaylist(List.of(channelId), Collections.emptyMap());
        if (list.isEmpty()) {
            return null;
        }
        return list.getFirst();
    }

    private List<DiscoveredChannelInfo> enrichChannelsWithUploadPlaylist(List<String> channelIds, Map<String, String> titles) throws IOException {
        List<DiscoveredChannelInfo> result = new ArrayList<>();

        int batchSize = 50;
        for (int i = 0; i < channelIds.size(); i += batchSize) {
            List<String> batch = channelIds.subList(i, Math.min(i + batchSize, channelIds.size()));

            YouTube.Channels.List request = youTube.channels()
                    .list(List.of("snippet", "contentDetails"))
                    .setId(batch);

            if (youtubeConfig.getApiKey() != null && !youtubeConfig.getApiKey().isBlank()) {
                request.setKey(youtubeConfig.getApiKey());
            }

            ChannelListResponse response = request.execute();
            if (response.getItems() != null) {
                for (Channel channel : response.getItems()) {
                    String id = channel.getId();
                    String title = (channel.getSnippet() != null) ? channel.getSnippet().getTitle() : titles.get(id);
                    String uploadsPlaylistId = null;
                    if (channel.getContentDetails() != null &&
                            channel.getContentDetails().getRelatedPlaylists() != null) {
                        uploadsPlaylistId = channel.getContentDetails().getRelatedPlaylists().getUploads();
                    }
                    result.add(new DiscoveredChannelInfo(id, title, uploadsPlaylistId));
                }
            }
        }

        return result;
    }
}

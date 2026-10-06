package com.monitorlatino.ytdetect.api.websub;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.WebSubStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class WebSubService {

    private static final Logger log = LoggerFactory.getLogger(WebSubService.class);
    private static final String YOUTUBE_FEED_URL_PREFIX = "https://www.youtube.com/feeds/videos.xml?channel_id=";

    private final YoutubeChannelRepository channelRepository;
    private final RestClient restClient;
    private final Clock clock;
    private final String hubUrl;
    private final String callbackUrl;
    private final String secret;

    public WebSubService(
            YoutubeChannelRepository channelRepository,
            Clock clock,
            RestClient.Builder restClientBuilder,
            @Value("${yt.websub.hub-url:https://pubsubhubbub.appspot.com/}") String hubUrl,
            @Value("${yt.websub.callback-url:https://example.com/api/v1/youtube/websub/callback}") String callbackUrl,
            @Value("${yt.websub.secret:ytdetect-websub-secret}") String secret) {
        this.channelRepository = channelRepository;
        this.clock = clock;
        this.hubUrl = hubUrl;
        this.callbackUrl = callbackUrl;
        this.secret = secret;
        this.restClient = restClientBuilder.build();
    }

    public boolean subscribe(String channelId) {
        return sendHubRequest(channelId, "subscribe");
    }

    public boolean unsubscribe(String channelId) {
        return sendHubRequest(channelId, "unsubscribe");
    }

    private boolean sendHubRequest(String channelId, String mode) {
        String topicUrl = YOUTUBE_FEED_URL_PREFIX + channelId;
        log.info("Sending WebSub {} request for channel {} to hub {}", mode, channelId, hubUrl);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("hub.callback", callbackUrl);
        formData.add("hub.mode", mode);
        formData.add("hub.topic", topicUrl);
        if (secret != null && !secret.isBlank()) {
            formData.add("hub.secret", secret);
        }
        if ("subscribe".equals(mode)) {
            formData.add("hub.lease_seconds", "864000");
        }

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(hubUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .toBodilessEntity();

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("WebSub {} request accepted by hub for channel {}", mode, channelId);
                channelRepository.findByChannelId(channelId).ifPresent(channel -> {
                    if ("subscribe".equals(mode)) {
                        channel.setWebsubStatus(WebSubStatus.PENDING);
                    } else {
                        channel.setWebsubStatus(WebSubStatus.EXPIRED);
                    }
                    channelRepository.save(channel);
                });
                return true;
            } else {
                log.warn("WebSub {} request returned status {} for channel {}", mode, response.getStatusCode(), channelId);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send WebSub {} request for channel {}: {}", mode, channelId, e.getMessage(), e);
            return false;
        }
    }

    public void renewExpiringSubscriptions() {
        Instant threshold = clock.instant().plus(Duration.ofDays(1));
        List<YoutubeChannel> enabledChannels = channelRepository.findByEnabledTrue();

        for (YoutubeChannel channel : enabledChannels) {
            if (channel.getWebsubLeaseExpiresAt() == null || channel.getWebsubLeaseExpiresAt().isBefore(threshold)
                    || channel.getWebsubStatus() != WebSubStatus.VERIFIED) {
                log.info("Renewing/subscribing WebSub for channel {}", channel.getChannelId());
                subscribe(channel.getChannelId());
            }
        }
    }
}

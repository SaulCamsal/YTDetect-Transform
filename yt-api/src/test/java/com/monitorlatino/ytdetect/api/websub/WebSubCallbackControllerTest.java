package com.monitorlatino.ytdetect.api.websub;

import com.monitorlatino.ytdetect.api.security.ApiKeyAuthFilter;
import com.monitorlatino.ytdetect.api.service.VideoDiscoveryService;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.WebSubStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebSubCallbackController.class)
@Import(ApiKeyAuthFilter.class)
class WebSubCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HmacSignatureValidator signatureValidator;

    @MockitoBean
    private AtomFeedParser atomFeedParser;

    @MockitoBean
    private VideoDiscoveryService videoDiscoveryService;

    @MockitoBean
    private YoutubeChannelRepository channelRepository;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName("GET callback: verify subscription intent, return challenge and update channel to VERIFIED")
    void shouldVerifySubscriptionAndReturnChallenge() throws Exception {
        String channelId = "UC123456";
        String topic = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
        String challenge = "random_hub_challenge_12345";

        YoutubeChannel channel = new YoutubeChannel(channelId, "Test Channel", ChannelOrigin.SUBSCRIPTION);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-05T12:00:00Z"));
        when(channelRepository.findByChannelId(channelId)).thenReturn(Optional.of(channel));

        mockMvc.perform(get("/api/v1/youtube/websub/callback")
                        .param("hub.mode", "subscribe")
                        .param("hub.topic", topic)
                        .param("hub.challenge", challenge)
                        .param("hub.lease_seconds", "432000"))
                .andExpect(status().isOk())
                .andExpect(content().string(challenge));

        verify(channelRepository).save(channel);
        org.junit.jupiter.api.Assertions.assertEquals(WebSubStatus.VERIFIED, channel.getWebsubStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                Instant.parse("2026-10-05T12:00:00Z").plusSeconds(432000),
                channel.getWebsubLeaseExpiresAt()
        );
    }

    @Test
    @DisplayName("GET callback: handle unsubscribe mode and update channel to INACTIVE")
    void shouldHandleUnsubscribeMode() throws Exception {
        String channelId = "UC123456";
        String topic = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
        String challenge = "challenge_unsub";

        YoutubeChannel channel = new YoutubeChannel(channelId, "Test Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setWebsubStatus(WebSubStatus.VERIFIED);
        when(channelRepository.findByChannelId(channelId)).thenReturn(Optional.of(channel));

        mockMvc.perform(get("/api/v1/youtube/websub/callback")
                        .param("hub.mode", "unsubscribe")
                        .param("hub.topic", topic)
                        .param("hub.challenge", challenge))
                .andExpect(status().isOk())
                .andExpect(content().string(challenge));

        verify(channelRepository).save(channel);
        org.junit.jupiter.api.Assertions.assertEquals(WebSubStatus.EXPIRED, channel.getWebsubStatus());
    }

    @Test
    @DisplayName("POST callback: valid HMAC signature triggers discovery and returns 200 OK")
    void shouldProcessNotificationWithValidSignature() throws Exception {
        String xmlBody = "<feed><entry><yt:videoId>v123</yt:videoId><yt:channelId>UC123</yt:channelId></entry></feed>";
        when(signatureValidator.isValid(any(), eq("sha1=validhash"), anyString())).thenReturn(true);
        when(atomFeedParser.parseFeed(anyString())).thenReturn(
                List.of(new AtomVideoEntry("v123", "UC123", "Video Title", Instant.now()))
        );

        mockMvc.perform(post("/api/v1/youtube/websub/callback")
                        .header("X-Hub-Signature", "sha1=validhash")
                        .contentType(MediaType.APPLICATION_ATOM_XML)
                        .content(xmlBody))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(videoDiscoveryService).discoverVideos(List.of("v123"), DiscoveredVia.WEBSUB);
    }

    @Test
    @DisplayName("POST callback: invalid HMAC signature returns 403 Forbidden")
    void shouldRejectNotificationWithInvalidSignature() throws Exception {
        String xmlBody = "<feed><entry></entry></feed>";
        when(signatureValidator.isValid(any(), eq("sha1=badhash"), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/youtube/websub/callback")
                        .header("X-Hub-Signature", "sha1=badhash")
                        .contentType(MediaType.APPLICATION_ATOM_XML)
                        .content(xmlBody))
                .andExpect(status().isForbidden());

        verify(videoDiscoveryService, never()).discoverVideos(any(), any());
    }
}

package com.monitorlatino.ytdetect.api.websub;

import com.monitorlatino.ytdetect.api.service.VideoDiscoveryService;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.WebSubStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/youtube/websub")
public class WebSubCallbackController {

    private static final Logger log = LoggerFactory.getLogger(WebSubCallbackController.class);
    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("channel_id=([a-zA-Z0-9_-]+)");

    private final HmacSignatureValidator signatureValidator;
    private final AtomFeedParser atomFeedParser;
    private final VideoDiscoveryService videoDiscoveryService;
    private final YoutubeChannelRepository channelRepository;
    private final Clock clock;
    private final String secret;

    public WebSubCallbackController(
            HmacSignatureValidator signatureValidator,
            AtomFeedParser atomFeedParser,
            VideoDiscoveryService videoDiscoveryService,
            YoutubeChannelRepository channelRepository,
            Clock clock,
            @Value("${yt.websub.secret:ytdetect-websub-secret}") String secret) {
        this.signatureValidator = signatureValidator;
        this.atomFeedParser = atomFeedParser;
        this.videoDiscoveryService = videoDiscoveryService;
        this.channelRepository = channelRepository;
        this.clock = clock;
        this.secret = secret;
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyCallback(
            @RequestParam(value = "hub.mode", required = false) String hubMode,
            @RequestParam(value = "hub.topic", required = false) String hubTopic,
            @RequestParam(value = "hub.challenge", required = false) String hubChallenge,
            @RequestParam(value = "hub.lease_seconds", required = false) Long leaseSeconds) {

        log.info("Received WebSub verification request: mode={}, topic={}, lease_seconds={}",
                hubMode, hubTopic, leaseSeconds);

        if (hubMode == null || hubChallenge == null) {
            log.warn("Missing hub.mode or hub.challenge in WebSub verification");
            return ResponseEntity.badRequest().body("Missing required parameters");
        }

        String channelId = extractChannelId(hubTopic);

        if ("subscribe".equalsIgnoreCase(hubMode)) {
            if (channelId != null) {
                Optional<YoutubeChannel> channelOpt = channelRepository.findByChannelId(channelId);
                channelOpt.ifPresent(channel -> {
                    channel.setWebsubStatus(WebSubStatus.VERIFIED);
                    if (leaseSeconds != null) {
                        channel.setWebsubLeaseExpiresAt(clock.instant().plusSeconds(leaseSeconds));
                    }
                    channelRepository.save(channel);
                    log.info("Channel {} WebSub status updated to VERIFIED (lease: {}s)", channelId, leaseSeconds);
                });
            }
            return ResponseEntity.ok(hubChallenge);
        } else if ("unsubscribe".equalsIgnoreCase(hubMode)) {
            if (channelId != null) {
                channelRepository.findByChannelId(channelId).ifPresent(channel -> {
                    channel.setWebsubStatus(WebSubStatus.EXPIRED);
                    channel.setWebsubLeaseExpiresAt(null);
                    channelRepository.save(channel);
                    log.info("Channel {} WebSub status updated to EXPIRED (unsubscribed)", channelId);
                });
            }
            return ResponseEntity.ok(hubChallenge);
        } else if ("denied".equalsIgnoreCase(hubMode)) {
            if (channelId != null) {
                channelRepository.findByChannelId(channelId).ifPresent(channel -> {
                    channel.setWebsubStatus(WebSubStatus.EXPIRED);
                    channelRepository.save(channel);
                    log.warn("Channel {} WebSub subscription DENIED by hub", channelId);
                });
            }
            return ResponseEntity.ok("OK");
        }

        return ResponseEntity.ok(hubChallenge);
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_ATOM_XML_VALUE)
    public ResponseEntity<String> receiveFeedNotificationXml(
            @RequestHeader(value = "X-Hub-Signature", required = false) String signatureHeader,
            @RequestBody byte[] rawPayload) {
        return processFeed(signatureHeader, rawPayload);
    }

    @PostMapping(value = "/callback")
    public ResponseEntity<String> receiveFeedNotificationFallback(
            @RequestHeader(value = "X-Hub-Signature", required = false) String signatureHeader,
            @RequestBody byte[] rawPayload) {
        return processFeed(signatureHeader, rawPayload);
    }

    private ResponseEntity<String> processFeed(String signatureHeader, byte[] rawPayload) {
        if (!signatureValidator.isValid(rawPayload, signatureHeader, secret)) {
            log.warn("WebSub callback signature validation failed. Signature: {}", signatureHeader);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        }

        String xml = new String(rawPayload, StandardCharsets.UTF_8);
        List<AtomVideoEntry> entries = atomFeedParser.parseFeed(xml);

        if (entries.isEmpty()) {
            log.info("WebSub feed notification received without video entries");
            return ResponseEntity.ok("OK");
        }

        log.info("Parsed {} video entries from WebSub notification", entries.size());
        List<String> videoIds = entries.stream()
                .map(AtomVideoEntry::videoId)
                .toList();

        videoDiscoveryService.discoverVideos(videoIds, DiscoveredVia.WEBSUB);
        return ResponseEntity.ok("OK");
    }

    private String extractChannelId(String topicUrl) {
        if (topicUrl == null) {
            return null;
        }
        Matcher matcher = CHANNEL_ID_PATTERN.matcher(topicUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

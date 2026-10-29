package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.YoutubeApiClient;
import com.monitorlatino.ytdetect.api.client.dto.DiscoveredChannelInfo;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "yt.reporting-sync.enabled", havingValue = "true", matchIfMissing = true)
public class ReportingStationSyncService {

    private static final Logger log = LoggerFactory.getLogger(ReportingStationSyncService.class);

    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("(?i)(?:channel/|c/|u/|user/)?(UC[a-zA-Z0-9_-]{22})");
    private static final Pattern HANDLE_PATTERN = Pattern.compile("(?i)(@[a-zA-Z0-9_.-]+)");
    private static final Pattern CUSTOM_URL_PATTERN = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?youtube\\.com/(?:c/|user/)?([a-zA-Z0-9_.-]+)(?:/(?:videos|featured|about|community|streams|podcasts))?/?$");

    private static final java.util.Set<String> RESERVED_PATH_SEGMENTS = java.util.Set.of(
            "watch", "playlist", "feed", "channel", "results", "shorts", "live", "embed", "attribution_link", "gaming", "music"
    );

    private final JdbcTemplate jdbcTemplate;
    private final YoutubeChannelRepository channelRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final Clock clock;
    private final String syncQuery;

    public ReportingStationSyncService(
            JdbcTemplate jdbcTemplate,
            YoutubeChannelRepository channelRepository,
            YoutubeApiClient youtubeApiClient,
            Clock clock,
            @Value("${yt.reporting-sync.query:SELECT si.stream_id, si.stream_desc, si.stream_owner, f.Folder FROM Reporting.dbo.vw_Station_Spots si INNER JOIN Reporting.dbo.stationfolders f ON f.stream_id = si.stream_id WHERE si.id_country = 27 AND si.stream_owner IS NOT NULL AND si.stream_owner LIKE '%youtube%'}") String syncQuery) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelRepository = channelRepository;
        this.youtubeApiClient = youtubeApiClient;
        this.clock = clock;
        this.syncQuery = syncQuery;
    }

    @Scheduled(cron = "${yt.reporting-sync.cron:0 0 * * * *}")
    @SchedulerLock(name = "reporting_station_sync", lockAtMostFor = "15m", lockAtLeastFor = "1m")
    public void scheduledSync() {
        log.info("Starting scheduled synchronization of YouTube stations from Reporting DB");
        int count = syncReportingStations();
        log.info("Completed scheduled synchronization. Synced/Updated {} channels.", count);
    }

    @Transactional
    public int syncReportingStations() {
        log.info("Querying Reporting database for country 27 YouTube stations...");

        try {
            return jdbcTemplate.query(syncQuery, rs -> {
                int processed = 0;
                while (rs.next()) {
                    int streamId = rs.getInt("stream_id");
                    String streamDesc = rs.getString("stream_desc");
                    String streamOwner = rs.getString("stream_owner");
                    String folder = rs.getString("Folder");

                    try {
                        if (processStation(streamId, streamDesc, streamOwner, folder)) {
                            processed++;
                        }
                    } catch (Exception ex) {
                        log.error("Failed processing reporting station {}: {}", streamId, ex.getMessage(), ex);
                    }
                }
                return processed;
            });
        } catch (Exception ex) {
            log.error("Failed executing sync query against Reporting DB: {}", ex.getMessage(), ex);
            return 0;
        }
    }

    private boolean processStation(int streamId, String streamDesc, String streamOwner, String folder) {
        String sanitizedFolder = (folder != null && !folder.isBlank()) ? folder.trim() : null;

        // Keep transcription.station_config in sync so Whisper/AWS automatically processes uploaded audio chunks
        upsertTranscriptionStationConfig(streamId, streamDesc, sanitizedFolder);

        // Check if stationId is already registered in youtube.youtube_channel
        Optional<YoutubeChannel> existingByStation = channelRepository.findOne(
                (root, query, cb) -> cb.equal(root.get("stationId"), streamId)
        );

        if (existingByStation.isPresent()) {
            YoutubeChannel existing = existingByStation.get();
            boolean updated = false;
            if (sanitizedFolder != null && !sanitizedFolder.equals(existing.getS3Folder())) {
                existing.setS3Folder(sanitizedFolder);
                updated = true;
            }
            if (updated) {
                existing.setUpdatedAt(clock.instant());
                channelRepository.save(existing);
                log.info("Updated existing channel {} for station {}: s3Folder={}", existing.getChannelId(), streamId, sanitizedFolder);
                return true;
            }
            return false;
        }

        // Parse stream_owner to extract handle or channel ID
        ParsedOwner parsed = parseStreamOwner(streamOwner);
        if (parsed == null) {
            log.warn("Cannot parse valid YouTube identifier from stream_owner '{}' for station {}", streamOwner, streamId);
            return false;
        }

        // Resolve channel from YouTube API or fallback
        DiscoveredChannelInfo info = resolveChannelInfo(parsed, streamDesc);
        if (info == null || info.channelId() == null || info.channelId().isBlank()) {
            log.warn("Could not resolve channel ID for station {} (owner: {})", streamId, streamOwner);
            return false;
        }

        String channelId = info.channelId();
        String channelTitle = (info.title() != null && !info.title().isBlank()) ? info.title() : streamDesc;
        String uploadsPlaylistId = info.uploadsPlaylistId();

        // Check if a channel with this channelId already exists without stationId
        Optional<YoutubeChannel> existingByChannelId = channelRepository.findByChannelId(channelId);
        YoutubeChannel channelToSave;
        Instant now = clock.instant();

        if (existingByChannelId.isPresent()) {
            channelToSave = existingByChannelId.get();
            channelToSave.setStationId(streamId);
            channelToSave.setS3Folder(sanitizedFolder);
            if (uploadsPlaylistId != null && channelToSave.getUploadsPlaylistId() == null) {
                channelToSave.setUploadsPlaylistId(uploadsPlaylistId);
            }
            channelToSave.setEnabled(true);
            channelToSave.setUpdatedAt(now);
            log.info("Linked existing channel {} to station {}", channelId, streamId);
        } else {
            channelToSave = new YoutubeChannel(channelId, channelTitle, ChannelOrigin.MANUAL);
            channelToSave.setStationId(streamId);
            channelToSave.setS3Folder(sanitizedFolder);
            channelToSave.setUploadsPlaylistId(uploadsPlaylistId);
            channelToSave.setEnabled(true);
            channelToSave.setTimezone("America/Mexico_City");
            channelToSave.setLanguageCode("es");
            channelToSave.setCreatedAt(now);
            channelToSave.setUpdatedAt(now);
            log.info("Created new YouTube channel {} for station {}: name={}, s3Folder={}",
                    channelId, streamId, channelTitle, sanitizedFolder);
        }

        channelRepository.save(channelToSave);
        return true;
    }

    void upsertTranscriptionStationConfig(int stationId, String streamDesc, String folder) {
        if (folder == null || folder.isBlank()) {
            return;
        }
        String cleanFolder = folder.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        String s3Prefix = cleanFolder + "/";
        String stationName = (streamDesc != null && !streamDesc.isBlank())
                ? streamDesc.trim()
                : cleanFolder.substring(cleanFolder.lastIndexOf('/') + 1);

        String sql = """
                MERGE transcription.station_config AS target
                USING (VALUES (?, ?, ?, CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))))
                    AS src (station_id, s3_prefix, station_name, timezone, enabled, process_weekends, priority, run_ai, notes)
                ON target.station_id = src.station_id
                WHEN MATCHED THEN
                    UPDATE SET
                        s3_prefix = src.s3_prefix,
                        station_name = src.station_name,
                        updated_at = GETUTCDATE()
                WHEN NOT MATCHED THEN
                    INSERT (station_id, s3_prefix, station_name, timezone, enabled, process_weekends, priority, run_ai, notes, created_at, updated_at)
                    VALUES (src.station_id, src.s3_prefix, src.station_name, src.timezone, src.enabled, src.process_weekends, src.priority, src.run_ai, src.notes, GETUTCDATE(), GETUTCDATE());
                """;

        try {
            jdbcTemplate.update(sql, stationId, s3Prefix, stationName);
            log.info("Synchronized transcription.station_config for station {}: prefix={}, name={}", stationId, s3Prefix, stationName);
        } catch (Exception ex) {
            log.warn("Could not upsert transcription.station_config for station {}: {}", stationId, ex.getMessage());
        }
    }

    private DiscoveredChannelInfo resolveChannelInfo(ParsedOwner parsed, String fallbackTitle) {
        if (parsed.handle() != null) {
            try {
                DiscoveredChannelInfo handleInfo = youtubeApiClient.fetchChannelByHandle(parsed.handle());
                if (handleInfo != null) {
                    return handleInfo;
                }
            } catch (IOException e) {
                log.warn("Failed resolving handle {} via YouTube API: {}", parsed.handle(), e.getMessage());
            }
        }

        if (parsed.channelId() != null) {
            try {
                DiscoveredChannelInfo idInfo = youtubeApiClient.fetchChannelInfo(parsed.channelId());
                if (idInfo != null) {
                    return idInfo;
                }
            } catch (IOException e) {
                log.warn("Failed resolving channelId {} via YouTube API: {}", parsed.channelId(), e.getMessage());
            }
            // Fallback: we know the channel ID and can derive the upload playlist (UU...)
            String derivedPlaylist = "UU" + parsed.channelId().substring(2);
            return new DiscoveredChannelInfo(parsed.channelId(), fallbackTitle, derivedPlaylist);
        }

        return null;
    }

    public static ParsedOwner parseStreamOwner(String streamOwner) {
        if (streamOwner == null || streamOwner.isBlank()) {
            return null;
        }

        String trimmed = streamOwner.trim();
        int queryIdx = trimmed.indexOf('?');
        if (queryIdx >= 0) {
            trimmed = trimmed.substring(0, queryIdx);
        }
        int hashIdx = trimmed.indexOf('#');
        if (hashIdx >= 0) {
            trimmed = trimmed.substring(0, hashIdx);
        }

        // 1. Check for Channel ID (UC...)
        Matcher channelMatcher = CHANNEL_ID_PATTERN.matcher(trimmed);
        if (channelMatcher.find()) {
            String channelId = channelMatcher.group(1);
            return new ParsedOwner(streamOwner.trim(), null, channelId);
        }

        // 2. Check for Handle (@...)
        Matcher handleMatcher = HANDLE_PATTERN.matcher(trimmed);
        if (handleMatcher.find()) {
            String handle = handleMatcher.group(1);
            return new ParsedOwner(streamOwner.trim(), handle, null);
        }

        // 3. Check for custom URL or handle without @ (e.g. youtube.com/AztecaNoticias)
        Matcher customMatcher = CUSTOM_URL_PATTERN.matcher(trimmed);
        if (customMatcher.find()) {
            String identifier = customMatcher.group(1);
            if (identifier != null && !identifier.isBlank() && !RESERVED_PATH_SEGMENTS.contains(identifier.toLowerCase())) {
                String handle = identifier.startsWith("@") ? identifier : "@" + identifier;
                return new ParsedOwner(streamOwner.trim(), handle, null);
            }
        }

        return null;
    }

    public record ParsedOwner(String raw, String handle, String channelId) {}
}

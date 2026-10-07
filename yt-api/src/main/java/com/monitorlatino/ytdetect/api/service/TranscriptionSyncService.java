package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.StationTranscriptionItemDto;
import com.monitorlatino.ytdetect.api.client.TranscriptionClient;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "yt.transcription-sync.enabled", havingValue = "true", matchIfMissing = true)
public class TranscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionSyncService.class);

    private final YoutubeAudioChunkRepository chunkRepository;
    private final YoutubeVideoRepository videoRepository;
    private final TranscriptionClient transcriptionClient;
    private final Clock clock;

    public TranscriptionSyncService(
            YoutubeAudioChunkRepository chunkRepository,
            YoutubeVideoRepository videoRepository,
            TranscriptionClient transcriptionClient,
            Clock clock) {
        this.chunkRepository = chunkRepository;
        this.videoRepository = videoRepository;
        this.transcriptionClient = transcriptionClient;
        this.clock = clock;
    }

    private record StationDateKey(Integer stationId, LocalDate date) {}

    @Scheduled(fixedDelayString = "${yt.transcription-sync.delay:60000}")
    @SchedulerLock(name = "youtube_transcription_sync", lockAtMostFor = "5m", lockAtLeastFor = "30s")
    @Transactional
    public void syncPendingTranscriptions() {
        List<YoutubeAudioChunk> pendingChunks = new ArrayList<>();
        pendingChunks.addAll(chunkRepository.findByTranscriptionStatus(TranscriptionStatus.QUEUED));
        pendingChunks.addAll(chunkRepository.findByTranscriptionStatus(TranscriptionStatus.PROCESSING));

        if (pendingChunks.isEmpty()) {
            return;
        }

        log.info("Found {} pending chunks to check against transcription API", pendingChunks.size());

        Map<StationDateKey, List<YoutubeAudioChunk>> chunksByStationAndDate = pendingChunks.stream()
                .filter(c -> c.getTranscriptionJobId() != null
                        && c.getVideo() != null
                        && c.getVideo().getChannel() != null
                        && c.getVideo().getChannel().getStationId() != null
                        && c.getAiredAtLocal() != null)
                .collect(Collectors.groupingBy(c -> new StationDateKey(
                        c.getVideo().getChannel().getStationId(),
                        c.getAiredAtLocal().toLocalDate()
                )));

        for (Map.Entry<StationDateKey, List<YoutubeAudioChunk>> entry : chunksByStationAndDate.entrySet()) {
            StationDateKey key = entry.getKey();
            List<YoutubeAudioChunk> chunks = entry.getValue();

            List<StationTranscriptionItemDto> apiItems = transcriptionClient.fetchStationTranscriptions(key.stationId(), key.date());
            if (apiItems == null || apiItems.isEmpty()) {
                continue;
            }

            Map<UUID, String> statusByJobId = apiItems.stream()
                    .filter(item -> item.jobId() != null && item.status() != null)
                    .collect(Collectors.toMap(StationTranscriptionItemDto::jobId, StationTranscriptionItemDto::status, (a, b) -> a));

            for (YoutubeAudioChunk chunk : chunks) {
                String apiStatus = statusByJobId.get(chunk.getTranscriptionJobId());
                if (apiStatus != null) {
                    TranscriptionStatus newStatus = parseStatus(apiStatus);
                    if (newStatus != null && newStatus != chunk.getTranscriptionStatus()) {
                        log.info("Updating chunk {} (jobId: {}) from {} to {}",
                                chunk.getId(), chunk.getTranscriptionJobId(), chunk.getTranscriptionStatus(), newStatus);
                        chunk.setTranscriptionStatus(newStatus);
                        if (newStatus == TranscriptionStatus.DEAD || newStatus == TranscriptionStatus.CANCELLED) {
                            chunk.setErrorCode("TRANSCRIPTION_FAILED");
                            chunk.setErrorMessage("External job status: " + apiStatus);
                        }
                        chunk.setUpdatedAt(clock.instant());
                        chunkRepository.save(chunk);
                    }
                }
            }
        }

        updateTranscribingVideos();
    }

    public void updateTranscribingVideos() {
        List<YoutubeVideo> transcribingVideos = videoRepository.findByStatus(VideoStatus.TRANSCRIBING);

        for (YoutubeVideo video : transcribingVideos) {
            List<YoutubeAudioChunk> chunks = chunkRepository.findByVideoVideoIdOrderByChunkIndexAsc(video.getVideoId());
            if (chunks.isEmpty()) {
                continue;
            }

            boolean allCompleted = chunks.stream()
                    .allMatch(c -> c.getTranscriptionStatus() == TranscriptionStatus.COMPLETED);

            if (allCompleted) {
                video.setStatus(VideoStatus.COMPLETED);
                video.setUpdatedAt(clock.instant());
                videoRepository.save(video);
                log.info("All {} chunks completed for video {}. Video marked as COMPLETED",
                        chunks.size(), video.getVideoId());
            } else {
                boolean anyFailed = chunks.stream()
                        .anyMatch(c -> c.getTranscriptionStatus() == TranscriptionStatus.DEAD
                                || c.getTranscriptionStatus() == TranscriptionStatus.CANCELLED);
                boolean nonePending = chunks.stream()
                        .noneMatch(c -> c.getTranscriptionStatus() == TranscriptionStatus.QUEUED
                                || c.getTranscriptionStatus() == TranscriptionStatus.PROCESSING);

                if (anyFailed && nonePending) {
                    video.setStatus(VideoStatus.FAILED);
                    video.setLastErrorCode("CHUNK_TRANSCRIPTION_FAILED");
                    video.setLastErrorMessage("One or more chunks failed transcription");
                    video.setUpdatedAt(clock.instant());
                    videoRepository.save(video);
                    log.error("Video {} marked as FAILED due to failed chunk transcriptions", video.getVideoId());
                }
            }
        }
    }

    private TranscriptionStatus parseStatus(String status) {
        if (status == null) return null;
        try {
            return TranscriptionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown transcription status received from API: {}", status);
            return null;
        }
    }
}

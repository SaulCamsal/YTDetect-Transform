package com.monitorlatino.ytdetect.worker.listener;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import com.monitorlatino.ytdetect.common.dto.AudioArrivalMessage;
import com.monitorlatino.ytdetect.common.dto.DownloadJobMessage;
import com.monitorlatino.ytdetect.worker.client.TranscriptionApiClient;
import com.monitorlatino.ytdetect.worker.client.TranscriptionJobRequest;
import com.monitorlatino.ytdetect.worker.client.TranscriptionJobResponse;
import com.monitorlatino.ytdetect.worker.service.AudioArrivalQueueService;
import com.monitorlatino.ytdetect.worker.service.AudioProcessingException;
import com.monitorlatino.ytdetect.worker.service.AudioProcessingService;
import com.monitorlatino.ytdetect.worker.service.S3AudioUploadService;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DownloadJobListener {

    private static final Logger log = LoggerFactory.getLogger(DownloadJobListener.class);

    private final YoutubeVideoRepository videoRepository;
    private final YoutubeAudioChunkRepository chunkRepository;
    private final AudioProcessingService audioProcessingService;
    private final S3AudioUploadService s3AudioUploadService;
    private final AudioArrivalQueueService audioArrivalQueueService;
    private final TranscriptionApiClient transcriptionApiClient;
    private final Clock clock;
    private final String tempDirBasePath;
    private final int maxAttempts;

    public DownloadJobListener(
            YoutubeVideoRepository videoRepository,
            YoutubeAudioChunkRepository chunkRepository,
            AudioProcessingService audioProcessingService,
            S3AudioUploadService s3AudioUploadService,
            AudioArrivalQueueService audioArrivalQueueService,
            TranscriptionApiClient transcriptionApiClient,
            Clock clock,
            @Value("${yt.worker.temp-dir:}") String tempDirBasePath,
            @Value("${yt.worker.max-attempts:3}") int maxAttempts) {
        this.videoRepository = videoRepository;
        this.chunkRepository = chunkRepository;
        this.audioProcessingService = audioProcessingService;
        this.s3AudioUploadService = s3AudioUploadService;
        this.audioArrivalQueueService = audioArrivalQueueService;
        this.transcriptionApiClient = transcriptionApiClient;
        this.clock = clock;
        this.tempDirBasePath = tempDirBasePath;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${yt.worker.poll-delay:30000}", initialDelay = 5000)
    public void pollAndProcessPendingDownloads() {
        List<YoutubeVideo> pendingVideos = videoRepository.findByStatus(VideoStatus.QUEUED_DOWNLOAD);
        if (pendingVideos.isEmpty()) {
            return;
        }

        log.info("Found {} videos with status QUEUED_DOWNLOAD, starting download processing", pendingVideos.size());
        for (YoutubeVideo video : pendingVideos) {
            String youtubeUrl = "https://www.youtube.com/watch?v=" + video.getVideoId();
            String channelId = (video.getChannel() != null) ? video.getChannel().getChannelId() : "";
            try {
                onDownloadJob(new DownloadJobMessage(video.getVideoId(), channelId, youtubeUrl));
            } catch (Exception e) {
                log.error("Failed processing queued video {}: {}", video.getVideoId(), e.getMessage());
            }
        }
    }

    public void onDownloadJob(DownloadJobMessage message) {
        log.info("Received download job for video: {} (channel: {})", message.videoId(), message.channelId());

        Optional<YoutubeVideo> videoOpt = videoRepository.findByVideoId(message.videoId());
        if (videoOpt.isEmpty()) {
            log.warn("Video {} not found in database, discarding message", message.videoId());
            return;
        }

        YoutubeVideo video = videoOpt.get();
        if (video.getStatus() == VideoStatus.TRANSCRIBING || video.getStatus() == VideoStatus.COMPLETED) {
            log.info("Video {} already in status {}, skipping processing", video.getVideoId(), video.getStatus());
            return;
        }

        File workDir = null;
        try {
            workDir = createWorkingDir(video.getVideoId());
            processVideoJob(video, message.youtubeUrl(), workDir);
        } catch (Exception e) {
            handleFailure(video, e);
            throw new RuntimeException("Failed to process video " + message.videoId(), e);
        } finally {
            if (workDir != null && workDir.exists()) {
                FileSystemUtils.deleteRecursively(workDir);
                log.debug("Cleaned up working directory: {}", workDir.getAbsolutePath());
            }
        }
    }

    @Transactional
    public void processVideoJob(YoutubeVideo video, String youtubeUrl, File workDir) throws Exception {
        video.setStatus(VideoStatus.DOWNLOADING);
        videoRepository.save(video);

        // 1. Download audio via yt-dlp
        File rawAudio = audioProcessingService.downloadAudio(youtubeUrl, workDir, "full_audio.mp3");

        // 2. Split and normalize audio via ffmpeg
        File chunksDir = new File(workDir, "chunks");
        List<File> chunkFiles = audioProcessingService.splitAndNormalizeAudio(rawAudio, chunksDir);
        video.setChunkCount(chunkFiles.size());
        video.setStatus(VideoStatus.AUDIO_READY);
        videoRepository.save(video);

        // 3. Upload chunks to S3 and dispatch transcription
        YoutubeChannel channel = video.getChannel();
        ZoneId zoneId = ZoneId.of(channel.getTimezone() != null ? channel.getTimezone() : "America/Mexico_City");
        LocalDateTime baseAiredAt = (video.getVirtualStart() != null)
                ? video.getVirtualStart()
                : (video.getPublishedAt() != null
                ? LocalDateTime.ofInstant(video.getPublishedAt(), zoneId)
                : LocalDateTime.now(clock.withZone(zoneId)));

        int chunkDurationSec = audioProcessingService.getChunkDurationSeconds();
        String targetBucket = s3AudioUploadService.getDefaultBucket();
        String targetPrefix = s3AudioUploadService.getDefaultPrefix();

        for (int i = 0; i < chunkFiles.size(); i++) {
            final int chunkIndex = i;
            File chunkFile = chunkFiles.get(chunkIndex);
            BigDecimal offsetSec = BigDecimal.valueOf((long) chunkIndex * chunkDurationSec);
            LocalDateTime chunkAiredAt = baseAiredAt.plusSeconds((long) chunkIndex * chunkDurationSec);

            String s3Key = s3AudioUploadService.buildS3Key(channel.getS3Folder(), channel.getChannelId(), video.getVideoId(), chunkAiredAt);
            String s3AudioUrl = s3AudioUploadService.uploadChunk(targetBucket, s3Key, chunkFile);

            YoutubeAudioChunk chunk = chunkRepository.findByVideoVideoIdAndChunkIndex(video.getVideoId(), chunkIndex)
                    .orElseGet(() -> new YoutubeAudioChunk(video, chunkIndex, offsetSec, chunkAiredAt, s3Key));

            chunk.setS3Key(s3Key);
            chunk.setDurationSeconds(BigDecimal.valueOf(chunkDurationSec));
            chunk.setTranscriptionStatus(TranscriptionStatus.QUEUED);

            if (channel.getStationId() != null) {
                UUID jobId = UUID.randomUUID();
                String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
                AudioArrivalMessage arrivalMessage = AudioArrivalMessage.of(
                        jobId,
                        channel.getStationId(),
                        s3AudioUrl,
                        fileName,
                        chunkAiredAt.toString(),
                        channel.getTimezone(),
                        channel.getLanguageCode()
                );

                try {
                    audioArrivalQueueService.sendAudioArrival(arrivalMessage);
                    chunk.setTranscriptionJobId(jobId);
                } catch (Exception ex) {
                    log.error("Failed to send chunk {} of video {} to audio-arrivals queue: {}",
                            chunkIndex, video.getVideoId(), ex.getMessage(), ex);
                    chunk.setErrorCode("SQS_DISPATCH_ERROR");
                    chunk.setErrorMessage(ex.getMessage());
                }
            } else {
                log.info("Channel {} has no stationId configured, audio uploaded but skipping transcription dispatch", channel.getChannelId());
            }

            chunkRepository.save(chunk);
        }

        video.setS3Bucket(targetBucket);
        video.setS3Prefix(targetPrefix);
        video.setStatus(channel.getStationId() != null ? VideoStatus.TRANSCRIBING : VideoStatus.AUDIO_READY);
        video.setAttemptCount(video.getAttemptCount() + 1);
        videoRepository.save(video);
        log.info("Successfully processed video {}. Status: {}", video.getVideoId(), video.getStatus());
    }

    @Transactional
    public void handleFailure(YoutubeVideo video, Exception e) {
        video.setAttemptCount(video.getAttemptCount() + 1);
        if (e instanceof AudioProcessingException ape) {
            video.setLastErrorCode(ape.getErrorCode());
        } else {
            video.setLastErrorCode("WORKER_ERROR");
        }

        String msg = e.getMessage() != null ? e.getMessage() : "Unknown processing error";
        video.setLastErrorMessage(msg.substring(0, Math.min(msg.length(), 1900)));

        if (video.getAttemptCount() >= maxAttempts) {
            video.setStatus(VideoStatus.FAILED);
            log.error("Video {} reached maximum attempts ({}), marked as FAILED", video.getVideoId(), maxAttempts);
        }

        videoRepository.save(video);
    }

    private File createWorkingDir(String videoId) throws Exception {
        if (tempDirBasePath != null && !tempDirBasePath.isBlank()) {
            File base = new File(tempDirBasePath);
            if (!base.exists()) {
                base.mkdirs();
            }
            File dir = new File(base, "yt_" + videoId + "_" + System.currentTimeMillis());
            dir.mkdirs();
            return dir;
        } else {
            return Files.createTempDirectory("yt_" + videoId + "_").toFile();
        }
    }
}

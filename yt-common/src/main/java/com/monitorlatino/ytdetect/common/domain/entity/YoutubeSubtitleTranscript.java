package com.monitorlatino.ytdetect.common.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        schema = "youtube",
        name = "youtube_subtitle_transcript",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_youtube_subtitle_transcript_video_chunk", columnNames = {"video_id", "chunk_index"})
        }
)
public class YoutubeSubtitleTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_seq")
    private Long resultSeq;

    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "job_id", nullable = false)
    private UUID jobId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", referencedColumnName = "video_id", nullable = false)
    private YoutubeVideo video;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "start_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal startSeconds;

    @Column(name = "end_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal endSeconds;

    @Column(name = "detected_language", length = 20)
    private String detectedLanguage = "es";

    @Column(name = "language_probability", precision = 5, scale = 4)
    private BigDecimal languageProbability = BigDecimal.valueOf(1.0000);

    @Column(name = "audio_duration_seconds", precision = 12, scale = 3)
    private BigDecimal audioDurationSeconds;

    @Column(name = "processing_seconds", precision = 12, scale = 3)
    private BigDecimal processingSeconds;

    @Column(name = "realtime_factor", precision = 12, scale = 2)
    private BigDecimal realtimeFactor;

    @Column(name = "model_name", length = 50)
    private String modelName = "youtube-subtitles";

    @Column(name = "worker_host", length = 255)
    private String workerHost;

    @Column(name = "transcript_text", columnDefinition = "VARCHAR(MAX)")
    private String transcriptText;

    @Column(name = "segments_gz", columnDefinition = "VARBINARY(MAX)")
    private byte[] segmentsGz;

    @Column(name = "segments_raw_bytes")
    private Integer segmentsRawBytes;

    @Column(name = "segment_count")
    private Integer segmentCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Emisora (youtube_channel.station_id) y hora local al aire del bloque, calculadas igual que
    // youtube_audio_chunk.aired_at_local. Las usa analytics.vw_contenido para tratar los subtítulos
    // como una transcripción más (detector de IA y reportes).
    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "aired_at_local")
    private LocalDateTime airedAtLocal;

    public YoutubeSubtitleTranscript() {
    }

    public YoutubeSubtitleTranscript(
            YoutubeVideo video,
            Integer chunkIndex,
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            BigDecimal audioDurationSeconds,
            String transcriptText,
            byte[] segmentsGz,
            Integer segmentsRawBytes,
            Integer segmentCount) {
        this.video = video;
        this.chunkIndex = chunkIndex;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.audioDurationSeconds = audioDurationSeconds;
        this.transcriptText = transcriptText;
        this.segmentsGz = segmentsGz;
        this.segmentsRawBytes = segmentsRawBytes;
        this.segmentCount = segmentCount;
    }

    public Long getResultSeq() {
        return resultSeq;
    }

    public void setResultSeq(Long resultSeq) {
        this.resultSeq = resultSeq;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public YoutubeVideo getVideo() {
        return video;
    }

    public void setVideo(YoutubeVideo video) {
        this.video = video;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public BigDecimal getStartSeconds() {
        return startSeconds;
    }

    public void setStartSeconds(BigDecimal startSeconds) {
        this.startSeconds = startSeconds;
    }

    public BigDecimal getEndSeconds() {
        return endSeconds;
    }

    public void setEndSeconds(BigDecimal endSeconds) {
        this.endSeconds = endSeconds;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }

    public BigDecimal getLanguageProbability() {
        return languageProbability;
    }

    public void setLanguageProbability(BigDecimal languageProbability) {
        this.languageProbability = languageProbability;
    }

    public BigDecimal getAudioDurationSeconds() {
        return audioDurationSeconds;
    }

    public void setAudioDurationSeconds(BigDecimal audioDurationSeconds) {
        this.audioDurationSeconds = audioDurationSeconds;
    }

    public BigDecimal getProcessingSeconds() {
        return processingSeconds;
    }

    public void setProcessingSeconds(BigDecimal processingSeconds) {
        this.processingSeconds = processingSeconds;
    }

    public BigDecimal getRealtimeFactor() {
        return realtimeFactor;
    }

    public void setRealtimeFactor(BigDecimal realtimeFactor) {
        this.realtimeFactor = realtimeFactor;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getWorkerHost() {
        return workerHost;
    }

    public void setWorkerHost(String workerHost) {
        this.workerHost = workerHost;
    }

    public String getTranscriptText() {
        return transcriptText;
    }

    public void setTranscriptText(String transcriptText) {
        this.transcriptText = transcriptText;
    }

    public byte[] getSegmentsGz() {
        return segmentsGz;
    }

    public void setSegmentsGz(byte[] segmentsGz) {
        this.segmentsGz = segmentsGz;
    }

    public Integer getSegmentsRawBytes() {
        return segmentsRawBytes;
    }

    public void setSegmentsRawBytes(Integer segmentsRawBytes) {
        this.segmentsRawBytes = segmentsRawBytes;
    }

    public Integer getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(Integer segmentCount) {
        this.segmentCount = segmentCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getStationId() {
        return stationId;
    }

    public void setStationId(Integer stationId) {
        this.stationId = stationId;
    }

    public LocalDateTime getAiredAtLocal() {
        return airedAtLocal;
    }

    public void setAiredAtLocal(LocalDateTime airedAtLocal) {
        this.airedAtLocal = airedAtLocal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoutubeSubtitleTranscript that)) return false;
        return Objects.equals(resultSeq, that.resultSeq);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(resultSeq);
    }
}

package com.monitorlatino.ytdetect.common.domain.entity;

import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        schema = "youtube",
        name = "youtube_audio_chunk",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_youtube_audio_chunk_video_index", columnNames = {"video_id", "chunk_index"})
        }
)
public class YoutubeAudioChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", referencedColumnName = "video_id", nullable = false)
    private YoutubeVideo video;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "offset_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal offsetSeconds;

    @Column(name = "duration_seconds", precision = 12, scale = 3)
    private BigDecimal durationSeconds;

    @Column(name = "aired_at_local", nullable = false)
    private LocalDateTime airedAtLocal;

    @Column(name = "s3_key", nullable = false, length = 1000)
    private String s3Key;

    @Column(name = "transcription_job_id")
    private UUID transcriptionJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcription_status", length = 30)
    private TranscriptionStatus transcriptionStatus;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public YoutubeAudioChunk() {
    }

    public YoutubeAudioChunk(YoutubeVideo video, Integer chunkIndex, BigDecimal offsetSeconds, LocalDateTime airedAtLocal, String s3Key) {
        this.video = video;
        this.chunkIndex = chunkIndex;
        this.offsetSeconds = offsetSeconds;
        this.airedAtLocal = airedAtLocal;
        this.s3Key = s3Key;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getOffsetSeconds() {
        return offsetSeconds;
    }

    public void setOffsetSeconds(BigDecimal offsetSeconds) {
        this.offsetSeconds = offsetSeconds;
    }

    public BigDecimal getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(BigDecimal durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public LocalDateTime getAiredAtLocal() {
        return airedAtLocal;
    }

    public void setAiredAtLocal(LocalDateTime airedAtLocal) {
        this.airedAtLocal = airedAtLocal;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public UUID getTranscriptionJobId() {
        return transcriptionJobId;
    }

    public void setTranscriptionJobId(UUID transcriptionJobId) {
        this.transcriptionJobId = transcriptionJobId;
    }

    public TranscriptionStatus getTranscriptionStatus() {
        return transcriptionStatus;
    }

    public void setTranscriptionStatus(TranscriptionStatus transcriptionStatus) {
        this.transcriptionStatus = transcriptionStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YoutubeAudioChunk that = (YoutubeAudioChunk) o;
        return Objects.equals(video != null ? video.getVideoId() : null, that.video != null ? that.video.getVideoId() : null) &&
                Objects.equals(chunkIndex, that.chunkIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(video != null ? video.getVideoId() : null, chunkIndex);
    }

    @Override
    public String toString() {
        return "YoutubeAudioChunk{" +
                "id=" + id +
                ", chunkIndex=" + chunkIndex +
                ", offsetSeconds=" + offsetSeconds +
                ", airedAtLocal=" + airedAtLocal +
                ", transcriptionStatus=" + transcriptionStatus +
                '}';
    }
}

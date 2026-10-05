package com.monitorlatino.ytdetect.common.domain.entity;

import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.LiveStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(schema = "youtube", name = "youtube_video")
public class YoutubeVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false, unique = true, length = 50)
    private String videoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", referencedColumnName = "channel_id", nullable = false)
    private YoutubeChannel channel;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "live_status", length = 20)
    private LiveStatus liveStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VideoStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "discovered_via", nullable = false, length = 20)
    private DiscoveredVia discoveredVia;

    @Column(name = "s3_bucket", length = 255)
    private String s3Bucket;

    @Column(name = "s3_prefix", length = 1000)
    private String s3Prefix;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "virtual_start")
    private LocalDateTime virtualStart;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 2000)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public YoutubeVideo() {
    }

    public YoutubeVideo(String videoId, YoutubeChannel channel, String title, VideoStatus status, DiscoveredVia discoveredVia) {
        this.videoId = videoId;
        this.channel = channel;
        this.title = title;
        this.status = status;
        this.discoveredVia = discoveredVia;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public YoutubeChannel getChannel() {
        return channel;
    }

    public void setChannel(YoutubeChannel channel) {
        this.channel = channel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public LiveStatus getLiveStatus() {
        return liveStatus;
    }

    public void setLiveStatus(LiveStatus liveStatus) {
        this.liveStatus = liveStatus;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
    }

    public DiscoveredVia getDiscoveredVia() {
        return discoveredVia;
    }

    public void setDiscoveredVia(DiscoveredVia discoveredVia) {
        this.discoveredVia = discoveredVia;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public String getS3Prefix() {
        return s3Prefix;
    }

    public void setS3Prefix(String s3Prefix) {
        this.s3Prefix = s3Prefix;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public LocalDateTime getVirtualStart() {
        return virtualStart;
    }

    public void setVirtualStart(LocalDateTime virtualStart) {
        this.virtualStart = virtualStart;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YoutubeVideo that = (YoutubeVideo) o;
        return Objects.equals(videoId, that.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoId);
    }

    @Override
    public String toString() {
        return "YoutubeVideo{" +
                "id=" + id +
                ", videoId='" + videoId + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", discoveredVia=" + discoveredVia +
                '}';
    }
}

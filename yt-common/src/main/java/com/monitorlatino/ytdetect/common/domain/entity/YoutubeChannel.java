package com.monitorlatino.ytdetect.common.domain.entity;

import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.WebSubStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(schema = "youtube", name = "youtube_channel")
public class YoutubeChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false, unique = true, length = 100)
    private String channelId;

    @Column(name = "channel_name", length = 255)
    private String channelName;

    @Column(name = "uploads_playlist_id", length = 100)
    private String uploadsPlaylistId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "America/Mexico_City";

    @Column(name = "language_code", nullable = false, length = 20)
    private String languageCode = "es";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private ChannelOrigin origin;

    @Column(name = "timeline_cursor")
    private LocalDateTime timelineCursor;

    @Enumerated(EnumType.STRING)
    @Column(name = "websub_status", length = 20)
    private WebSubStatus websubStatus;

    @Column(name = "websub_lease_expires_at")
    private Instant websubLeaseExpiresAt;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public YoutubeChannel() {
    }

    public YoutubeChannel(String channelId, String channelName, ChannelOrigin origin) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.origin = origin;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getUploadsPlaylistId() {
        return uploadsPlaylistId;
    }

    public void setUploadsPlaylistId(String uploadsPlaylistId) {
        this.uploadsPlaylistId = uploadsPlaylistId;
    }

    public Integer getStationId() {
        return stationId;
    }

    public void setStationId(Integer stationId) {
        this.stationId = stationId;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ChannelOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(ChannelOrigin origin) {
        this.origin = origin;
    }

    public LocalDateTime getTimelineCursor() {
        return timelineCursor;
    }

    public void setTimelineCursor(LocalDateTime timelineCursor) {
        this.timelineCursor = timelineCursor;
    }

    public WebSubStatus getWebsubStatus() {
        return websubStatus;
    }

    public void setWebsubStatus(WebSubStatus websubStatus) {
        this.websubStatus = websubStatus;
    }

    public Instant getWebsubLeaseExpiresAt() {
        return websubLeaseExpiresAt;
    }

    public void setWebsubLeaseExpiresAt(Instant websubLeaseExpiresAt) {
        this.websubLeaseExpiresAt = websubLeaseExpiresAt;
    }

    public Instant getLastCheckAt() {
        return lastCheckAt;
    }

    public void setLastCheckAt(Instant lastCheckAt) {
        this.lastCheckAt = lastCheckAt;
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
        YoutubeChannel that = (YoutubeChannel) o;
        return Objects.equals(channelId, that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId);
    }

    @Override
    public String toString() {
        return "YoutubeChannel{" +
                "id=" + id +
                ", channelId='" + channelId + '\'' +
                ", channelName='" + channelName + '\'' +
                ", stationId=" + stationId +
                ", enabled=" + enabled +
                ", origin=" + origin +
                '}';
    }
}

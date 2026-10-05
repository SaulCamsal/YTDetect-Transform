package com.monitorlatino.ytdetect.common.domain.repository;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import com.monitorlatino.ytdetect.common.domain.enums.DiscoveredVia;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class YoutubeRepositoryTest {

    @Autowired
    private YoutubeChannelRepository channelRepository;

    @Autowired
    private YoutubeVideoRepository videoRepository;

    @Autowired
    private YoutubeAudioChunkRepository chunkRepository;

    @Test
    @DisplayName("Should persist and find YoutubeChannel by channelId")
    void shouldPersistAndFindChannel() {
        YoutubeChannel channel = new YoutubeChannel("UC_TEST_1", "Test Channel", ChannelOrigin.SUBSCRIPTION);
        channel.setEnabled(true);
        channel.setStationId(101);
        channelRepository.save(channel);

        Optional<YoutubeChannel> found = channelRepository.findByChannelId("UC_TEST_1");
        assertThat(found).isPresent();
        assertThat(found.get().getChannelName()).isEqualTo("Test Channel");
        assertThat(found.get().isEnabled()).isTrue();
        assertThat(found.get().getStationId()).isEqualTo(101);
        assertThat(found.get().getVersion()).isNotNull();
    }

    @Test
    @DisplayName("Should persist and find YoutubeVideo with channel reference")
    void shouldPersistAndFindVideo() {
        YoutubeChannel channel = new YoutubeChannel("UC_TEST_2", "Test Channel 2", ChannelOrigin.MANUAL);
        channel = channelRepository.save(channel);

        YoutubeVideo video = new YoutubeVideo("vid_123", channel, "Awesome Video", VideoStatus.DISCOVERED, DiscoveredVia.WEBSUB);
        video.setDurationSeconds(1800);
        videoRepository.save(video);

        Optional<YoutubeVideo> found = videoRepository.findByVideoId("vid_123");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Awesome Video");
        assertThat(found.get().getStatus()).isEqualTo(VideoStatus.DISCOVERED);
        assertThat(found.get().getChannel().getChannelId()).isEqualTo("UC_TEST_2");
        assertThat(found.get().getDurationSeconds()).isEqualTo(1800);
    }

    @Test
    @DisplayName("Should persist and order YoutubeAudioChunks by chunk_index")
    void shouldPersistAndOrderChunks() {
        YoutubeChannel channel = new YoutubeChannel("UC_TEST_3", "Test Channel 3", ChannelOrigin.SUBSCRIPTION);
        channel = channelRepository.save(channel);

        YoutubeVideo video = new YoutubeVideo("vid_456", channel, "Video With Chunks", VideoStatus.DOWNLOADING, DiscoveredVia.POLLING);
        video = videoRepository.save(video);

        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 12, 0);
        UUID jobId = UUID.randomUUID();

        YoutubeAudioChunk chunk1 = new YoutubeAudioChunk(
                video,
                1,
                BigDecimal.valueOf(300.000),
                now.plusMinutes(5),
                "s3://bucket/vid_456/chunk_0001.mp3"
        );
        chunk1.setTranscriptionJobId(jobId);
        chunk1.setTranscriptionStatus(TranscriptionStatus.QUEUED);

        YoutubeAudioChunk chunk0 = new YoutubeAudioChunk(
                video,
                0,
                BigDecimal.ZERO,
                now,
                "s3://bucket/vid_456/chunk_0000.mp3"
        );
        chunk0.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

        chunkRepository.save(chunk1);
        chunkRepository.save(chunk0);

        List<YoutubeAudioChunk> ordered = chunkRepository.findByVideoVideoIdOrderByChunkIndexAsc("vid_456");
        assertThat(ordered).hasSize(2);
        assertThat(ordered.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(ordered.get(0).getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(ordered.get(1).getChunkIndex()).isEqualTo(1);
        assertThat(ordered.get(1).getTranscriptionJobId()).isEqualTo(jobId);

        Optional<YoutubeAudioChunk> byJobId = chunkRepository.findByTranscriptionJobId(jobId);
        assertThat(byJobId).isPresent();
        assertThat(byJobId.get().getChunkIndex()).isEqualTo(1);
    }
}

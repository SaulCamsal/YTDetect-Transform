package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.dto.AudioChunkResponse;
import com.monitorlatino.ytdetect.api.dto.VideoResponse;
import com.monitorlatino.ytdetect.api.dto.VideoTranscriptionResponse;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class YoutubeVideoService {

    private final YoutubeVideoRepository videoRepository;
    private final YoutubeAudioChunkRepository chunkRepository;

    public YoutubeVideoService(
            YoutubeVideoRepository videoRepository,
            YoutubeAudioChunkRepository chunkRepository) {
        this.videoRepository = videoRepository;
        this.chunkRepository = chunkRepository;
    }

    @Transactional(readOnly = true)
    public Page<VideoResponse> getVideos(String channelId, VideoStatus status, Pageable pageable) {
        Specification<YoutubeVideo> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channelId != null && !channelId.isBlank()) {
                predicates.add(cb.equal(root.get("channel").get("channelId"), channelId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return videoRepository.findAll(spec, pageable).map(VideoResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<VideoResponse> getVideoByVideoId(String videoId) {
        return videoRepository.findByVideoId(videoId).map(VideoResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<VideoTranscriptionResponse> getVideoTranscription(String videoId) {
        return videoRepository.findByVideoId(videoId).map(video -> {
            List<YoutubeAudioChunk> chunks = chunkRepository.findByVideoVideoIdOrderByChunkIndexAsc(videoId);
            List<AudioChunkResponse> chunkResponses = chunks.stream()
                    .map(AudioChunkResponse::from)
                    .toList();
            return new VideoTranscriptionResponse(video.getVideoId(), video.getStatus(), video.getChunkCount(), chunkResponses);
        });
    }
}

package com.monitorlatino.ytdetect.common.domain.repository;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.enums.TranscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface YoutubeAudioChunkRepository extends JpaRepository<YoutubeAudioChunk, Long>, JpaSpecificationExecutor<YoutubeAudioChunk> {

    List<YoutubeAudioChunk> findByVideoVideoIdOrderByChunkIndexAsc(String videoId);

    Optional<YoutubeAudioChunk> findByVideoVideoIdAndChunkIndex(String videoId, Integer chunkIndex);

    Optional<YoutubeAudioChunk> findByTranscriptionJobId(UUID transcriptionJobId);

    List<YoutubeAudioChunk> findByTranscriptionStatus(TranscriptionStatus transcriptionStatus);

    List<YoutubeAudioChunk> findByVideoVideoId(String videoId);
}

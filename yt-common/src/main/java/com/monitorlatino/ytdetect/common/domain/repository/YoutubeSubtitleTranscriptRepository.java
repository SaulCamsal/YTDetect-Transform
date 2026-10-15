package com.monitorlatino.ytdetect.common.domain.repository;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeSubtitleTranscript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface YoutubeSubtitleTranscriptRepository
        extends JpaRepository<YoutubeSubtitleTranscript, Long>, JpaSpecificationExecutor<YoutubeSubtitleTranscript> {

    List<YoutubeSubtitleTranscript> findByVideoVideoIdOrderByChunkIndexAsc(String videoId);

    Optional<YoutubeSubtitleTranscript> findByVideoVideoIdAndChunkIndex(String videoId, Integer chunkIndex);

    Optional<YoutubeSubtitleTranscript> findByJobId(UUID jobId);

    List<YoutubeSubtitleTranscript> findByVideoVideoId(String videoId);
}

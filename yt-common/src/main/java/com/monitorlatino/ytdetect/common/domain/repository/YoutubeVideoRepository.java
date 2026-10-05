package com.monitorlatino.ytdetect.common.domain.repository;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface YoutubeVideoRepository extends JpaRepository<YoutubeVideo, Long>, JpaSpecificationExecutor<YoutubeVideo> {

    Optional<YoutubeVideo> findByVideoId(String videoId);

    boolean existsByVideoId(String videoId);

    List<YoutubeVideo> findByStatus(VideoStatus status);

    List<YoutubeVideo> findByStatusIn(Collection<VideoStatus> statuses);

    List<YoutubeVideo> findByChannelChannelId(String channelId);
}

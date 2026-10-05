package com.monitorlatino.ytdetect.common.domain.repository;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YoutubeChannelRepository extends JpaRepository<YoutubeChannel, Long>, JpaSpecificationExecutor<YoutubeChannel> {

    Optional<YoutubeChannel> findByChannelId(String channelId);

    boolean existsByChannelId(String channelId);

    List<YoutubeChannel> findByEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM YoutubeChannel c WHERE c.channelId = :channelId")
    Optional<YoutubeChannel> findByChannelIdForUpdate(@Param("channelId") String channelId);
}

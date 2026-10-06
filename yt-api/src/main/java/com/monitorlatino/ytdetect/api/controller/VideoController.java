package com.monitorlatino.ytdetect.api.controller;

import com.monitorlatino.ytdetect.api.dto.VideoResponse;
import com.monitorlatino.ytdetect.api.dto.VideoTranscriptionResponse;
import com.monitorlatino.ytdetect.api.service.YoutubeVideoService;
import com.monitorlatino.ytdetect.common.domain.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/youtube/videos")
public class VideoController {

    private final YoutubeVideoService videoService;

    public VideoController(YoutubeVideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    public ResponseEntity<Page<VideoResponse>> getVideos(
            @RequestParam(value = "channelId", required = false) String channelId,
            @RequestParam(value = "status", required = false) VideoStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(videoService.getVideos(channelId, status, pageable));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<VideoResponse> getVideoByVideoId(@PathVariable("videoId") String videoId) {
        return videoService.getVideoByVideoId(videoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{videoId}/transcription")
    public ResponseEntity<VideoTranscriptionResponse> getVideoTranscription(@PathVariable("videoId") String videoId) {
        return videoService.getVideoTranscription(videoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

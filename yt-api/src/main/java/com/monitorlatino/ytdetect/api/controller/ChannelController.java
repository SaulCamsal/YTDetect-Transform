package com.monitorlatino.ytdetect.api.controller;

import com.monitorlatino.ytdetect.api.dto.ChannelResponse;
import com.monitorlatino.ytdetect.api.dto.CreateChannelRequest;
import com.monitorlatino.ytdetect.api.dto.SyncResultResponse;
import com.monitorlatino.ytdetect.api.dto.UpdateChannelRequest;
import com.monitorlatino.ytdetect.api.service.YoutubeChannelService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/youtube/channels")
public class ChannelController {

    private final YoutubeChannelService channelService;

    public ChannelController(YoutubeChannelService channelService) {
        this.channelService = channelService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ChannelResponse> createChannel(@Valid @RequestBody CreateChannelRequest request) {
        ChannelResponse response = channelService.createChannel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ChannelResponse>> getChannels(
            @RequestParam(required = false) Boolean enabled,
            Pageable pageable) {
        Page<ChannelResponse> response = channelService.getChannels(enabled, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChannelResponse> getChannelById(@PathVariable Long id) {
        ChannelResponse response = channelService.getChannelById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChannelResponse> updateChannel(
            @PathVariable Long id,
            @RequestBody UpdateChannelRequest request) {
        ChannelResponse response = channelService.updateChannel(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResultResponse> triggerSync() {
        SyncResultResponse response = channelService.triggerSync();
        return ResponseEntity.ok(response);
    }
}

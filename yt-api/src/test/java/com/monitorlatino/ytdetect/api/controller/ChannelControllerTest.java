package com.monitorlatino.ytdetect.api.controller;

import com.monitorlatino.ytdetect.api.dto.ChannelResponse;
import com.monitorlatino.ytdetect.api.dto.CreateChannelRequest;
import com.monitorlatino.ytdetect.api.dto.SyncResultResponse;
import com.monitorlatino.ytdetect.api.dto.UpdateChannelRequest;
import com.monitorlatino.ytdetect.api.security.ApiKeyAuthFilter;
import com.monitorlatino.ytdetect.api.service.YoutubeChannelService;
import com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChannelController.class)
@Import(ApiKeyAuthFilter.class)
class ChannelControllerTest {

    private static final String API_KEY = "default-secret-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YoutubeChannelService channelService;

    @Test
    @DisplayName("Should return 401 when X-API-Key is missing")
    void shouldReturn401WhenApiKeyMissing() throws Exception {
        mockMvc.perform(get("/api/v1/youtube/channels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 200 and paged channels when X-API-Key is provided")
    void shouldReturnChannelsWhenAuthorized() throws Exception {
        ChannelResponse channelResponse = new ChannelResponse(
                1L,
                "UC123",
                "Test Channel",
                "UU123",
                101,
                "America/Mexico_City",
                "es",
                true,
                ChannelOrigin.SUBSCRIPTION,
                null,
                null,
                Instant.now(),
                null
        );

        when(channelService.getChannels(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(channelResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/youtube/channels")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].channelId").value("UC123"))
                .andExpect(jsonPath("$.content[0].channelName").value("Test Channel"));
    }

    @Test
    @DisplayName("Should create channel and return 201 Created")
    void shouldCreateChannel() throws Exception {
        CreateChannelRequest request = new CreateChannelRequest(
                "UC_NEW",
                "New Channel",
                102,
                "America/Mexico_City",
                "es"
        );

        ChannelResponse response = new ChannelResponse(
                2L,
                "UC_NEW",
                "New Channel",
                "UU_NEW",
                102,
                "America/Mexico_City",
                "es",
                true,
                ChannelOrigin.MANUAL,
                null,
                null,
                Instant.now(),
                null
        );

        when(channelService.createChannel(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/youtube/channels")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channelId":"UC_NEW","channelName":"New Channel","stationId":102,"timezone":"America/Mexico_City","languageCode":"es"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.channelId").value("UC_NEW"))
                .andExpect(jsonPath("$.origin").value("MANUAL"));
    }

    @Test
    @DisplayName("Should patch channel and return 200 OK")
    void shouldPatchChannel() throws Exception {
        UpdateChannelRequest request = new UpdateChannelRequest(
                true,
                105,
                null,
                null
        );

        ChannelResponse response = new ChannelResponse(
                1L,
                "UC123",
                "Test Channel",
                "UU123",
                105,
                "America/Mexico_City",
                "es",
                true,
                ChannelOrigin.SUBSCRIPTION,
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        when(channelService.updateChannel(eq(1L), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/youtube/channels/1")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"stationId":105}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value(105))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("Should trigger sync and return 200 OK")
    void shouldTriggerSync() throws Exception {
        SyncResultResponse syncResult = new SyncResultResponse("COMPLETED", 10, 2, 8, "Sync successful");
        when(channelService.triggerSync()).thenReturn(syncResult);

        mockMvc.perform(post("/api/v1/youtube/channels/sync")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.discoveredCount").value(10))
                .andExpect(jsonPath("$.newlyAddedCount").value(2));
    }
}

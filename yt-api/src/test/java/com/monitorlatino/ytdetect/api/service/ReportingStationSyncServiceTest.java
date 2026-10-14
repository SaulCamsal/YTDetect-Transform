package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.YoutubeApiClient;
import com.monitorlatino.ytdetect.api.client.dto.DiscoveredChannelInfo;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeChannel;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingStationSyncServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private YoutubeChannelRepository channelRepository;
    @Mock
    private YoutubeApiClient youtubeApiClient;

    private Clock fixedClock;
    private ReportingStationSyncService syncService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC);
        syncService = new ReportingStationSyncService(
                jdbcTemplate,
                channelRepository,
                youtubeApiClient,
                fixedClock,
                "SELECT stream_id, stream_desc, stream_owner, Folder FROM dummy"
        );
    }

    @Test
    @DisplayName("Should correctly parse various YouTube stream_owner URL formats")
    void shouldParseStreamOwnerFormats() {
        // Handle in URL
        var p1 = ReportingStationSyncService.parseStreamOwner("https://www.youtube.com/@milenio");
        assertNotNull(p1);
        assertEquals("@milenio", p1.handle());

        // Handle raw
        var p2 = ReportingStationSyncService.parseStreamOwner("@GrupoFormula-RF");
        assertNotNull(p2);
        assertEquals("@GrupoFormula-RF", p2.handle());

        // Channel ID in URL
        var p3 = ReportingStationSyncService.parseStreamOwner("https://www.youtube.com/channel/UCfxHplbcoJK9m70c4VyTIxg");
        assertNotNull(p3);
        assertEquals("UCfxHplbcoJK9m70c4VyTIxg", p3.channelId());

        // Invalid or empty
        assertNull(ReportingStationSyncService.parseStreamOwner(""));
        assertNull(ReportingStationSyncService.parseStreamOwner("https://example.com/radio"));
    }

    @Test
    @DisplayName("Should sync and register a new YouTube station resolved by handle")
    @SuppressWarnings("unchecked")
    void shouldSyncNewStationByHandle() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt("stream_id")).thenReturn(6321);
        when(rs.getString("stream_desc")).thenReturn("milenio");
        when(rs.getString("stream_owner")).thenReturn("https://www.youtube.com/@milenio");
        when(rs.getString("Folder")).thenReturn("/CHART/YT/MILENIO");

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class))).thenAnswer(invocation -> {
            ResultSetExtractor<Integer> extractor = invocation.getArgument(1);
            return extractor.extractData(rs);
        });

        // Station not yet in DB
        when(channelRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        // Resolve handle via YouTube API
        when(youtubeApiClient.fetchChannelByHandle("@milenio"))
                .thenReturn(new DiscoveredChannelInfo("UCfxHplbcoJK9m70c4VyTIxg", "Milenio Oficial", "UUfxHplbcoJK9m70c4VyTIxg"));
        when(channelRepository.findByChannelId("UCfxHplbcoJK9m70c4VyTIxg")).thenReturn(Optional.empty());

        int synced = syncService.syncReportingStations();
        assertEquals(1, synced);

        ArgumentCaptor<YoutubeChannel> captor = ArgumentCaptor.forClass(YoutubeChannel.class);
        verify(channelRepository).save(captor.capture());
        YoutubeChannel saved = captor.getValue();

        assertEquals("UCfxHplbcoJK9m70c4VyTIxg", saved.getChannelId());
        assertEquals("Milenio Oficial", saved.getChannelName());
        assertEquals(6321, saved.getStationId());
        assertEquals("/CHART/YT/MILENIO", saved.getS3Folder());
        assertEquals("UUfxHplbcoJK9m70c4VyTIxg", saved.getUploadsPlaylistId());
        assertTrue(saved.isEnabled());
    }

    @Test
    @DisplayName("Should update s3_folder if existing station has changed folder")
    @SuppressWarnings("unchecked")
    void shouldUpdateS3FolderForExistingStation() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt("stream_id")).thenReturn(6322);
        when(rs.getString("stream_desc")).thenReturn("GrupoFormula-RF");
        when(rs.getString("stream_owner")).thenReturn("https://www.youtube.com/@GrupoFormula-RF");
        when(rs.getString("Folder")).thenReturn("/CHART/YT/GRUPOFORMULA-RF_NEW");

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class))).thenAnswer(invocation -> {
            ResultSetExtractor<Integer> extractor = invocation.getArgument(1);
            return extractor.extractData(rs);
        });

        YoutubeChannel existing = new YoutubeChannel("UCzmd9Aj2wmPggWZJpLdBB8Q", "GrupoFormula-RF", com.monitorlatino.ytdetect.common.domain.enums.ChannelOrigin.MANUAL);
        existing.setStationId(6322);
        existing.setS3Folder("/CHART/YT/GRUPOFORMULA-RF_OLD");

        when(channelRepository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));

        int synced = syncService.syncReportingStations();
        assertEquals(1, synced);

        assertEquals("/CHART/YT/GRUPOFORMULA-RF_NEW", existing.getS3Folder());
        verify(channelRepository).save(existing);
        verify(youtubeApiClient, never()).fetchChannelByHandle(anyString());
    }
}

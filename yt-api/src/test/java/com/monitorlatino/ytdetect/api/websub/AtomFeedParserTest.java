package com.monitorlatino.ytdetect.api.websub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomFeedParserTest {

    private AtomFeedParser parser;

    @BeforeEach
    void setUp() {
        parser = new AtomFeedParser();
    }

    @Test
    @DisplayName("Should parse standard YouTube Atom feed with single entry")
    void shouldParseStandardYoutubeAtomFeed() {
        String xml = """
                <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
                      xmlns="http://www.w3.org/2005/Atom">
                  <link rel="is" href="http://www.youtube.com/channel/UC123456"/>
                  <title>Canal Ejemplo</title>
                  <updated>2026-10-05T12:00:00+00:00</updated>
                  <entry>
                    <id>yt:video:abc123xyz</id>
                    <yt:videoId>abc123xyz</yt:videoId>
                    <yt:channelId>UC123456</yt:channelId>
                    <title>Título del video de prueba</title>
                    <link rel="alternate" href="https://www.youtube.com/watch?v=abc123xyz"/>
                    <published>2026-10-05T11:59:00+00:00</published>
                    <updated>2026-10-05T12:00:00+00:00</updated>
                  </entry>
                </feed>
                """;

        List<AtomVideoEntry> entries = parser.parseFeed(xml);

        assertNotNull(entries);
        assertEquals(1, entries.size());

        AtomVideoEntry entry = entries.get(0);
        assertEquals("abc123xyz", entry.videoId());
        assertEquals("UC123456", entry.channelId());
        assertEquals("Título del video de prueba", entry.title());
        assertNotNull(entry.publishedAt());
    }

    @Test
    @DisplayName("Should parse feed with multiple entries")
    void shouldParseFeedWithMultipleEntries() {
        String xml = """
                <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <yt:videoId>video_01</yt:videoId>
                    <yt:channelId>UC_CHAN_1</yt:channelId>
                    <title>Video 1</title>
                  </entry>
                  <entry>
                    <yt:videoId>video_02</yt:videoId>
                    <yt:channelId>UC_CHAN_1</yt:channelId>
                    <title>Video 2</title>
                  </entry>
                </feed>
                """;

        List<AtomVideoEntry> entries = parser.parseFeed(xml);

        assertEquals(2, entries.size());
        assertEquals("video_01", entries.get(0).videoId());
        assertEquals("video_02", entries.get(1).videoId());
    }

    @Test
    @DisplayName("Should return empty list for null or empty XML")
    void shouldReturnEmptyForNullOrEmptyXml() {
        assertTrue(parser.parseFeed(null).isEmpty());
        assertTrue(parser.parseFeed("   ").isEmpty());
    }

    @Test
    @DisplayName("Should handle malformed XML gracefully")
    void shouldHandleMalformedXmlGracefully() {
        String malformedXml = "<feed><entry><videoId>unfinished";
        List<AtomVideoEntry> entries = parser.parseFeed(malformedXml);

        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }
}

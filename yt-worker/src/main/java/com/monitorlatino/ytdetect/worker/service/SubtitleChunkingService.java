package com.monitorlatino.ytdetect.worker.service;

import com.monitorlatino.ytdetect.common.domain.entity.YoutubeSubtitleTranscript;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.worker.dto.SubtitleCue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.JsonGenerator;
import software.amazon.awssdk.thirdparty.jackson.core.JsonParser;
import software.amazon.awssdk.thirdparty.jackson.core.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
public class SubtitleChunkingService {

    private static final Logger log = LoggerFactory.getLogger(SubtitleChunkingService.class);

    private final Clock clock;
    private final int chunkDurationSeconds;
    private final String hostname;
    private final JsonFactory jsonFactory = new JsonFactory();

    public SubtitleChunkingService(
            Clock clock,
            @Value("${yt.worker.chunk-duration-seconds:300}") int chunkDurationSeconds) {
        this.clock = clock;
        this.chunkDurationSeconds = chunkDurationSeconds;
        this.hostname = resolveHostname();
    }

    public List<YoutubeSubtitleTranscript> createChunks(
            YoutubeVideo video,
            List<SubtitleCue> cues,
            String detectedLanguage) {

        if (cues == null || cues.isEmpty()) {
            return List.of();
        }

        double maxCueEnd = cues.stream()
                .mapToDouble(SubtitleCue::endSeconds)
                .max()
                .orElse(0.0);

        double totalDuration = (video.getDurationSeconds() != null && video.getDurationSeconds() > 0)
                ? video.getDurationSeconds()
                : maxCueEnd;

        if (totalDuration <= 0) {
            totalDuration = maxCueEnd;
        }

        int chunkCount = (int) Math.ceil(totalDuration / chunkDurationSeconds);
        if (chunkCount <= 0) {
            chunkCount = 1;
        }

        log.info("Chunking {} subtitle cues across {} chunks (total duration: {}s) for video {}",
                cues.size(), chunkCount, totalDuration, video.getVideoId());

        List<YoutubeSubtitleTranscript> transcripts = new ArrayList<>(chunkCount);

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            double chunkStart = chunkIndex * chunkDurationSeconds;
            double chunkEnd = Math.min((chunkIndex + 1) * chunkDurationSeconds, totalDuration);
            if (chunkEnd < chunkStart) {
                chunkEnd = chunkStart + chunkDurationSeconds;
            }

            final double windowStart = chunkStart;
            final double windowEnd = (chunkIndex + 1) * chunkDurationSeconds;

            List<SubtitleCue> chunkCues = cues.stream()
                    .filter(c -> c.startSeconds() >= windowStart && c.startSeconds() < windowEnd)
                    .toList();

            StringBuilder textBuilder = new StringBuilder();
            for (SubtitleCue cue : chunkCues) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append(" ");
                }
                textBuilder.append(cue.text());
            }

            try {
                byte[] rawJsonBytes = serializeSegmentsToJson(chunkCues, chunkStart);
                byte[] gzipBytes = compressGzip(rawJsonBytes);

                YoutubeSubtitleTranscript transcript = new YoutubeSubtitleTranscript();
                transcript.setId(UUID.randomUUID());
                transcript.setJobId(UUID.randomUUID());
                transcript.setVideo(video);
                transcript.setChunkIndex(chunkIndex);
                transcript.setStartSeconds(BigDecimal.valueOf(chunkStart).setScale(3, RoundingMode.HALF_UP));
                transcript.setEndSeconds(BigDecimal.valueOf(chunkEnd).setScale(3, RoundingMode.HALF_UP));
                transcript.setAudioDurationSeconds(BigDecimal.valueOf(chunkEnd - chunkStart).setScale(3, RoundingMode.HALF_UP));
                transcript.setDetectedLanguage(detectedLanguage != null ? detectedLanguage : "es");
                transcript.setLanguageProbability(BigDecimal.valueOf(1.0000));
                transcript.setModelName("youtube-subtitles");
                transcript.setWorkerHost(hostname);
                transcript.setTranscriptText(textBuilder.toString());
                transcript.setSegmentsGz(gzipBytes);
                transcript.setSegmentsRawBytes(rawJsonBytes.length);
                transcript.setSegmentCount(chunkCues.size());
                transcript.setCreatedAt(LocalDateTime.now(clock));

                transcripts.add(transcript);
            } catch (IOException e) {
                log.error("Failed to serialize or compress subtitle segments for chunk {} of video {}: {}",
                        chunkIndex, video.getVideoId(), e.getMessage(), e);
                throw new RuntimeException("Error generating subtitle chunk " + chunkIndex, e);
            }
        }

        return transcripts;
    }

    public byte[] serializeSegmentsToJson(List<SubtitleCue> chunkCues, double chunkStart) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            gen.writeStartArray();
            for (SubtitleCue cue : chunkCues) {
                double relStart = round(Math.max(0.0, cue.startSeconds() - chunkStart), 3);
                double relEnd = round(Math.max(relStart, cue.endSeconds() - chunkStart), 3);

                gen.writeStartObject();
                gen.writeNumberField("start", relStart);
                gen.writeNumberField("end", relEnd);
                gen.writeStringField("text", cue.text());
                gen.writeNumberField("avgLogprob", 0.0);
                gen.writeNumberField("noSpeechProb", 0.0);
                gen.writeEndObject();
            }
            gen.writeEndArray();
        }
        return baos.toByteArray();
    }

    public List<Map<String, Object>> parseSegmentsJson(byte[] jsonBytes) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (JsonParser parser = jsonFactory.createParser(jsonBytes)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String field = parser.currentName();
                        parser.nextToken();
                        if ("start".equals(field) || "end".equals(field) || "avgLogprob".equals(field) || "noSpeechProb".equals(field)) {
                            map.put(field, parser.getDoubleValue());
                        } else if ("text".equals(field)) {
                            map.put(field, parser.getText());
                        } else {
                            parser.skipChildren();
                        }
                    }
                    list.add(map);
                }
            }
        }
        return list;
    }

    public byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    public byte[] decompressGzip(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            gzis.transferTo(baos);
            return baos.toByteArray();
        }
    }

    private double round(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "yt-worker";
        }
    }
}

package com.monitorlatino.ytdetect.api.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.monitorlatino.ytdetect.api.client.TranscriptionClient;
import com.monitorlatino.ytdetect.api.client.TranscriptionJobResultDto;
import com.monitorlatino.ytdetect.api.client.TranscriptionSegmentDto;
import com.monitorlatino.ytdetect.api.dto.FullVideoTranscriptResponse;
import com.monitorlatino.ytdetect.api.dto.TranscriptSegmentResponse;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeSubtitleTranscript;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeSubtitleTranscriptRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@Service
public class VideoTranscriptAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscriptAssemblyService.class);
    private static final Type SEGMENT_LIST_TYPE = new TypeToken<List<TranscriptionSegmentDto>>() {}.getType();

    private final YoutubeVideoRepository videoRepository;
    private final YoutubeAudioChunkRepository chunkRepository;
    private final YoutubeSubtitleTranscriptRepository subtitleTranscriptRepository;
    private final TranscriptionClient transcriptionClient;
    private final Gson gson;

    public VideoTranscriptAssemblyService(
            YoutubeVideoRepository videoRepository,
            YoutubeAudioChunkRepository chunkRepository,
            YoutubeSubtitleTranscriptRepository subtitleTranscriptRepository,
            TranscriptionClient transcriptionClient) {
        this.videoRepository = videoRepository;
        this.chunkRepository = chunkRepository;
        this.subtitleTranscriptRepository = subtitleTranscriptRepository;
        this.transcriptionClient = transcriptionClient;
        this.gson = new Gson();
    }

    @Transactional(readOnly = true)
    public Optional<FullVideoTranscriptResponse> assembleTranscript(String videoId) {
        Optional<YoutubeVideo> videoOpt = videoRepository.findByVideoId(videoId);
        if (videoOpt.isEmpty()) {
            return Optional.empty();
        }

        YoutubeVideo video = videoOpt.get();
        List<YoutubeAudioChunk> chunks = chunkRepository.findByVideoVideoIdOrderByChunkIndexAsc(videoId);

        if (chunks.isEmpty()) {
            List<YoutubeSubtitleTranscript> subTranscripts = subtitleTranscriptRepository.findByVideoVideoIdOrderByChunkIndexAsc(videoId);
            if (!subTranscripts.isEmpty()) {
                return Optional.of(assembleFromSubtitles(video, subTranscripts));
            }
        }

        List<TranscriptSegmentResponse> allSegments = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();

        for (YoutubeAudioChunk chunk : chunks) {
            if (chunk.getTranscriptionJobId() == null) {
                continue;
            }

            double chunkOffset = chunk.getOffsetSeconds() != null
                    ? chunk.getOffsetSeconds().doubleValue()
                    : (chunk.getChunkIndex() != null ? chunk.getChunkIndex() * 300.0 : 0.0);

            Optional<TranscriptionJobResultDto> resultOpt = transcriptionClient.fetchJobResult(chunk.getTranscriptionJobId());
            if (resultOpt.isPresent() && resultOpt.get().segments() != null) {
                for (TranscriptionSegmentDto segment : resultOpt.get().segments()) {
                    double globalStart = chunkOffset + (segment.start() != null ? segment.start() : 0.0);
                    double globalEnd = chunkOffset + (segment.end() != null ? segment.end() : 0.0);
                    String text = segment.text() != null ? segment.text().trim() : "";

                    if (!text.isEmpty()) {
                        allSegments.add(new TranscriptSegmentResponse(globalStart, globalEnd, text));
                        if (!fullTextBuilder.isEmpty()) {
                            fullTextBuilder.append(" ");
                        }
                        fullTextBuilder.append(text);
                    }
                }
            }
        }

        return Optional.of(new FullVideoTranscriptResponse(
                video.getVideoId(),
                video.getTitle(),
                video.getStatus(),
                video.getChunkCount(),
                fullTextBuilder.toString(),
                allSegments
        ));
    }

    private FullVideoTranscriptResponse assembleFromSubtitles(
            YoutubeVideo video,
            List<YoutubeSubtitleTranscript> subTranscripts) {

        List<TranscriptSegmentResponse> allSegments = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();

        for (YoutubeSubtitleTranscript subChunk : subTranscripts) {
            double chunkOffset = subChunk.getStartSeconds() != null
                    ? subChunk.getStartSeconds().doubleValue()
                    : (subChunk.getChunkIndex() != null ? subChunk.getChunkIndex() * 300.0 : 0.0);

            if (subChunk.getSegmentsGz() != null && subChunk.getSegmentsGz().length > 0) {
                try {
                    byte[] decompressed = decompressGzip(subChunk.getSegmentsGz());
                    String json = new String(decompressed, StandardCharsets.UTF_8);
                    List<TranscriptionSegmentDto> segments = gson.fromJson(json, SEGMENT_LIST_TYPE);
                    if (segments != null) {
                        for (TranscriptionSegmentDto segment : segments) {
                            double globalStart = chunkOffset + (segment.start() != null ? segment.start() : 0.0);
                            double globalEnd = chunkOffset + (segment.end() != null ? segment.end() : 0.0);
                            String text = segment.text() != null ? segment.text().trim() : "";

                            if (!text.isEmpty()) {
                                allSegments.add(new TranscriptSegmentResponse(globalStart, globalEnd, text));
                                if (!fullTextBuilder.isEmpty()) {
                                    fullTextBuilder.append(" ");
                                }
                                fullTextBuilder.append(text);
                            }
                        }
                    }
                } catch (IOException e) {
                    log.warn("Failed to decompress segments_gz for chunk {} of video {}: {}",
                            subChunk.getChunkIndex(), video.getVideoId(), e.getMessage());
                }
            } else if (subChunk.getTranscriptText() != null && !subChunk.getTranscriptText().isBlank()) {
                if (!fullTextBuilder.isEmpty()) {
                    fullTextBuilder.append(" ");
                }
                fullTextBuilder.append(subChunk.getTranscriptText().trim());
            }
        }

        return new FullVideoTranscriptResponse(
                video.getVideoId(),
                video.getTitle(),
                video.getStatus(),
                video.getChunkCount(),
                fullTextBuilder.toString(),
                allSegments
        );
    }

    private byte[] decompressGzip(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            gzis.transferTo(baos);
            return baos.toByteArray();
        }
    }
}

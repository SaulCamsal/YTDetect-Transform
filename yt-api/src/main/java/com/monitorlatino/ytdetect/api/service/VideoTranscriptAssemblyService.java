package com.monitorlatino.ytdetect.api.service;

import com.monitorlatino.ytdetect.api.client.TranscriptionClient;
import com.monitorlatino.ytdetect.api.client.TranscriptionJobResultDto;
import com.monitorlatino.ytdetect.api.client.TranscriptionSegmentDto;
import com.monitorlatino.ytdetect.api.dto.FullVideoTranscriptResponse;
import com.monitorlatino.ytdetect.api.dto.TranscriptSegmentResponse;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeAudioChunk;
import com.monitorlatino.ytdetect.common.domain.entity.YoutubeVideo;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeAudioChunkRepository;
import com.monitorlatino.ytdetect.common.domain.repository.YoutubeVideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class VideoTranscriptAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscriptAssemblyService.class);

    private final YoutubeVideoRepository videoRepository;
    private final YoutubeAudioChunkRepository chunkRepository;
    private final TranscriptionClient transcriptionClient;

    public VideoTranscriptAssemblyService(
            YoutubeVideoRepository videoRepository,
            YoutubeAudioChunkRepository chunkRepository,
            TranscriptionClient transcriptionClient) {
        this.videoRepository = videoRepository;
        this.chunkRepository = chunkRepository;
        this.transcriptionClient = transcriptionClient;
    }

    @Transactional(readOnly = true)
    public Optional<FullVideoTranscriptResponse> assembleTranscript(String videoId) {
        Optional<YoutubeVideo> videoOpt = videoRepository.findByVideoId(videoId);
        if (videoOpt.isEmpty()) {
            return Optional.empty();
        }

        YoutubeVideo video = videoOpt.get();
        List<YoutubeAudioChunk> chunks = chunkRepository.findByVideoVideoIdOrderByChunkIndexAsc(videoId);

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
}

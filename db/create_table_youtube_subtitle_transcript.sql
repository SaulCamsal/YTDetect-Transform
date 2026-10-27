-- =====================================================================
-- MonitorTranscription DB: Tabla para transcripciones directas de subtítulos
-- Base de datos: MonitorTranscription (SQL Server)
-- Esquema: youtube
-- =====================================================================

USE MonitorTranscription;
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE schema_id = SCHEMA_ID('youtube') AND name = 'youtube_subtitle_transcript')
BEGIN
    CREATE TABLE youtube.youtube_subtitle_transcript (
        result_seq             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_youtube_subtitle_transcript PRIMARY KEY,
        id                     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        job_id                 UNIQUEIDENTIFIER NOT NULL,
        video_id               VARCHAR(50) NOT NULL CONSTRAINT fk_youtube_subtitle_transcript_video REFERENCES youtube.youtube_video(video_id),
        chunk_index            INT NOT NULL,
        start_seconds          DECIMAL(12,3) NOT NULL,
        end_seconds            DECIMAL(12,3) NOT NULL,
        detected_language      VARCHAR(20) NULL DEFAULT 'es',
        language_probability   DECIMAL(5,4) NULL DEFAULT 1.0000,
        audio_duration_seconds DECIMAL(12,3) NULL,
        processing_seconds     DECIMAL(12,3) NULL,
        realtime_factor        DECIMAL(12,2) NULL,
        model_name             VARCHAR(50) NULL DEFAULT 'youtube-subtitles',
        worker_host            VARCHAR(255) NULL,
        transcript_text        VARCHAR(MAX) COLLATE Latin1_General_100_CI_AI_SC_UTF8 NULL,  -- igual que transcription_result
        segments_gz            VARBINARY(MAX) NULL,
        segments_raw_bytes     INT NULL,
        segment_count          INT NULL,
        created_at             DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
        station_id             INT NULL,            -- youtube_channel.station_id (= analytics.fuente.fuente_id)
        aired_at_local         DATETIME2(0) NULL,   -- misma regla que youtube_audio_chunk.aired_at_local
        CONSTRAINT uq_youtube_subtitle_transcript_video_chunk UNIQUE (video_id, chunk_index)
    );

    CREATE INDEX ix_youtube_subtitle_transcript_video ON youtube.youtube_subtitle_transcript(video_id, chunk_index);
    -- analytics.vw_contenido filtra por fecha al aire (detector de IA y reportes)
    CREATE INDEX ix_youtube_subtitle_transcript_aired ON youtube.youtube_subtitle_transcript(aired_at_local) INCLUDE (station_id);
    CREATE UNIQUE INDEX ux_youtube_subtitle_transcript_id ON youtube.youtube_subtitle_transcript(id);
END
GO

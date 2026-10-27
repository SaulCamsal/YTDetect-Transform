-- =====================================================================
-- MonitorTranscription DB: station_id + aired_at_local en subtítulos
-- Correr ANTES de desplegar el yt-worker que llena estas columnas.
-- Idempotente: se puede correr más de una vez.
--
-- Después correr en TranscriptApi: sql/34_analytics_youtube_subtitulos.sql
-- (llena los registros existentes y une los subtítulos a analytics.vw_contenido).
-- =====================================================================
USE MonitorTranscription;
GO

IF COL_LENGTH('youtube.youtube_subtitle_transcript', 'station_id') IS NULL
    ALTER TABLE youtube.youtube_subtitle_transcript ADD station_id INT NULL;
IF COL_LENGTH('youtube.youtube_subtitle_transcript', 'aired_at_local') IS NULL
    ALTER TABLE youtube.youtube_subtitle_transcript ADD aired_at_local DATETIME2(0) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_youtube_subtitle_transcript_aired'
               AND object_id = OBJECT_ID('youtube.youtube_subtitle_transcript'))
    CREATE INDEX ix_youtube_subtitle_transcript_aired
        ON youtube.youtube_subtitle_transcript (aired_at_local) INCLUDE (station_id);
GO

-- El detector busca cada contenido por id (analytics.vw_contenido.contenido_id)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_youtube_subtitle_transcript_id'
               AND object_id = OBJECT_ID('youtube.youtube_subtitle_transcript'))
    CREATE UNIQUE INDEX ux_youtube_subtitle_transcript_id
        ON youtube.youtube_subtitle_transcript (id);
GO

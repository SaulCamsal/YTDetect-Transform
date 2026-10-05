-- =====================================================================
-- YTDetect-Transform - Esquema "youtube" (V1)
-- Base de datos: MonitorTranscription (SQL Server, AWS RDS)
--
-- RESPONSABLE: humano (se ejecuta manualmente en SSMS).
-- ORDEN: 1) 00_create_schema.sql   2) este archivo   3) permissions.sql
--
-- Reglas del archivo:
--  - SIN "GO": el mismo script lo usan los tests (Testcontainers) y SSMS.
--  - Cada sentencia termina en ";".
--  - Fechas *_at = UTC. aired_at_local / timeline_cursor / virtual_start = hora local del canal.
--  - Es un CONTRATO: los agentes NO lo modifican. Ver specs/contracts/C1-database.md
-- =====================================================================

CREATE TABLE youtube.youtube_channel (
    id                      BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_youtube_channel PRIMARY KEY,
    channel_id              VARCHAR(100)  NOT NULL CONSTRAINT uq_youtube_channel_channel_id UNIQUE,
    channel_name            NVARCHAR(255) NULL,
    uploads_playlist_id     VARCHAR(100)  NULL,
    station_id              INT           NULL,
    timezone                VARCHAR(64)   NOT NULL CONSTRAINT df_youtube_channel_timezone DEFAULT 'America/Mexico_City',
    language_code           VARCHAR(20)   NOT NULL CONSTRAINT df_youtube_channel_language DEFAULT 'es',
    enabled                 BIT           NOT NULL CONSTRAINT df_youtube_channel_enabled DEFAULT 0,
    origin                  VARCHAR(20)   NOT NULL,
    timeline_cursor         DATETIME2(0)  NULL,
    websub_status           VARCHAR(20)   NULL,
    websub_lease_expires_at DATETIME2(0)  NULL,
    last_check_at           DATETIME2(3)  NULL,
    created_at              DATETIME2(3)  NOT NULL CONSTRAINT df_youtube_channel_created DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)  NULL,
    version                 BIGINT        NOT NULL CONSTRAINT df_youtube_channel_version DEFAULT 0
);

CREATE TABLE youtube.youtube_video (
    id                 BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_youtube_video PRIMARY KEY,
    video_id           VARCHAR(50)    NOT NULL CONSTRAINT uq_youtube_video_video_id UNIQUE,
    channel_id         VARCHAR(100)   NOT NULL
                       CONSTRAINT fk_youtube_video_channel REFERENCES youtube.youtube_channel(channel_id),
    title              NVARCHAR(500)  NULL,
    description        NVARCHAR(MAX)  NULL,
    published_at       DATETIME2(0)   NULL,
    duration_seconds   INT            NULL,
    live_status        VARCHAR(20)    NULL,
    status             VARCHAR(30)    NOT NULL,
    discovered_via     VARCHAR(20)    NOT NULL,
    s3_bucket          VARCHAR(255)   NULL,
    s3_prefix          VARCHAR(1000)  NULL,
    chunk_count        INT            NULL,
    virtual_start      DATETIME2(0)   NULL,
    attempt_count      INT            NOT NULL CONSTRAINT df_youtube_video_attempts DEFAULT 0,
    last_error_code    VARCHAR(50)    NULL,
    last_error_message NVARCHAR(2000) NULL,
    created_at         DATETIME2(3)   NOT NULL CONSTRAINT df_youtube_video_created DEFAULT SYSUTCDATETIME(),
    updated_at         DATETIME2(3)   NULL,
    version            BIGINT         NOT NULL CONSTRAINT df_youtube_video_version DEFAULT 0
);

CREATE INDEX ix_youtube_video_status  ON youtube.youtube_video(status, created_at);
CREATE INDEX ix_youtube_video_updated ON youtube.youtube_video(status, updated_at);
CREATE INDEX ix_youtube_video_channel ON youtube.youtube_video(channel_id, published_at);

CREATE TABLE youtube.youtube_audio_chunk (
    id                   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_youtube_audio_chunk PRIMARY KEY,
    video_id             VARCHAR(50)      NOT NULL
                         CONSTRAINT fk_youtube_audio_chunk_video REFERENCES youtube.youtube_video(video_id),
    chunk_index          INT              NOT NULL,
    offset_seconds       DECIMAL(12,3)    NOT NULL,
    duration_seconds     DECIMAL(12,3)    NULL,
    aired_at_local       DATETIME2(0)     NOT NULL,
    s3_key               VARCHAR(1000)    NOT NULL,
    transcription_job_id UNIQUEIDENTIFIER NULL,
    transcription_status VARCHAR(30)      NULL,
    error_code           VARCHAR(50)      NULL,
    error_message        NVARCHAR(2000)   NULL,
    created_at           DATETIME2(3)     NOT NULL CONSTRAINT df_youtube_audio_chunk_created DEFAULT SYSUTCDATETIME(),
    updated_at           DATETIME2(3)     NULL,
    CONSTRAINT uq_youtube_audio_chunk_video_index UNIQUE (video_id, chunk_index)
);

CREATE INDEX ix_youtube_audio_chunk_tstatus ON youtube.youtube_audio_chunk(transcription_status)
    INCLUDE (video_id, transcription_job_id, aired_at_local);

CREATE UNIQUE INDEX uq_youtube_audio_chunk_job ON youtube.youtube_audio_chunk(transcription_job_id)
    WHERE transcription_job_id IS NOT NULL;

-- Candados de schedulers (ShedLock) para que solo una instancia de yt-api ejecute cada tarea.
CREATE TABLE youtube.shedlock (
    name       VARCHAR(64)  NOT NULL CONSTRAINT pk_youtube_shedlock PRIMARY KEY,
    lock_until DATETIME2(3) NOT NULL,
    locked_at  DATETIME2(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

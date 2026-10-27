# C1 - Contrato de Base de Datos (Esquema `youtube`)

La base de datos es SQL Server (`MonitorTranscription`). Todo nuestro trabajo vive en el esquema `youtube`.

Las siguientes tablas están creadas manualmente, por lo que las entidades JPA deben mapear *exactamente* a estas definiciones.

### 1. Tabla `youtube.youtube_channel`
- `id` (BIGINT IDENTITY PRIMARY KEY)
- `channel_id` (VARCHAR(100) NOT NULL UNIQUE)
- `channel_name` (NVARCHAR(255) NULL)
- `uploads_playlist_id` (VARCHAR(100) NULL)
- `station_id` (INT NULL) - ID mapeado hacia el sistema externo (stream_id en vw_Station_Spots)
- `s3_folder` (VARCHAR(255) NULL) - Carpeta de S3 de la estación (Folder en stationfolders, ej. /CHART/YT/MILENIO)
- `timezone` (VARCHAR(64) NOT NULL)
- `language_code` (VARCHAR(20) NOT NULL)
- `enabled` (BIT NOT NULL)
- `origin` (VARCHAR(20) NOT NULL)
- `timeline_cursor` (DATETIME2(0) NULL)
- `websub_status` (VARCHAR(20) NULL)
- `websub_lease_expires_at` (DATETIME2(0) NULL)
- `last_check_at` (DATETIME2(3) NULL)
- `created_at` (DATETIME2(3) NOT NULL)
- `updated_at` (DATETIME2(3) NULL)
- `version` (BIGINT NOT NULL) - para Optimistic Locking (`@Version`)

### 2. Tabla `youtube.youtube_video`
- `id` (BIGINT IDENTITY PRIMARY KEY)
- `video_id` (VARCHAR(50) NOT NULL UNIQUE)
- `channel_id` (VARCHAR(100) NOT NULL) -> FK a `youtube_channel(channel_id)`
- `title` (NVARCHAR(500) NULL)
- `description` (NVARCHAR(MAX) NULL)
- `published_at` (DATETIME2(0) NULL)
- `duration_seconds` (INT NULL)
- `live_status` (VARCHAR(20) NULL)
- `status` (VARCHAR(30) NOT NULL)
- `discovered_via` (VARCHAR(20) NOT NULL)
- `s3_bucket` (VARCHAR(255) NULL)
- `s3_prefix` (VARCHAR(1000) NULL)
- `chunk_count` (INT NULL)
- `virtual_start` (DATETIME2(0) NULL)
- `attempt_count` (INT NOT NULL)
- `last_error_code` (VARCHAR(50) NULL)
- `last_error_message` (NVARCHAR(2000) NULL)
- `created_at` (DATETIME2(3) NOT NULL)
- `updated_at` (DATETIME2(3) NULL)
- `version` (BIGINT NOT NULL) - para Optimistic Locking (`@Version`)

### 3. Tabla `youtube.youtube_audio_chunk`
- `id` (BIGINT IDENTITY PRIMARY KEY)
- `video_id` (VARCHAR(50) NOT NULL) -> FK a `youtube_video(video_id)`
- `chunk_index` (INT NOT NULL)
- `offset_seconds` (DECIMAL(12,3) NOT NULL)
- `duration_seconds` (DECIMAL(12,3) NULL)
- `aired_at_local` (DATETIME2(0) NOT NULL)
- `s3_key` (VARCHAR(1000) NOT NULL)
- `transcription_job_id` (UNIQUEIDENTIFIER NULL)
- `transcription_status` (VARCHAR(30) NULL)
- `error_code` (VARCHAR(50) NULL)
- `error_message` (NVARCHAR(2000) NULL)
- `created_at` (DATETIME2(3) NOT NULL)
- `updated_at` (DATETIME2(3) NULL)

### 4. Tabla `youtube.shedlock`
Tabla estándar para la librería ShedLock (manejo de ejecución concurrente de Schedulers).
- `name` (VARCHAR(64) PRIMARY KEY)
- `lock_until` (DATETIME2(3) NOT NULL)
- `locked_at` (DATETIME2(3) NOT NULL)
- `locked_by` (VARCHAR(255) NOT NULL)

### 5. Tabla `youtube.youtube_subtitle_transcript`
Almacena transcripciones obtenidas directamente desde los subtítulos de YouTube fragmentadas en bloques de 5 minutos, con formato idéntico a `transcription.transcription_result`.
- `result_seq` (BIGINT IDENTITY PRIMARY KEY)
- `id` (UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID())
- `job_id` (UNIQUEIDENTIFIER NOT NULL)
- `video_id` (VARCHAR(50) NOT NULL) -> FK a `youtube_video(video_id)`
- `chunk_index` (INT NOT NULL)
- `start_seconds` (DECIMAL(12,3) NOT NULL)
- `end_seconds` (DECIMAL(12,3) NOT NULL)
- `detected_language` (VARCHAR(20) NULL)
- `language_probability` (DECIMAL(5,4) NULL)
- `audio_duration_seconds` (DECIMAL(12,3) NULL)
- `processing_seconds` (DECIMAL(12,3) NULL)
- `realtime_factor` (DECIMAL(12,2) NULL)
- `model_name` (VARCHAR(50) NULL)
- `worker_host` (VARCHAR(255) NULL)
- `transcript_text` (VARCHAR(MAX) COLLATE Latin1_General_100_CI_AI_SC_UTF8 NULL) - misma collation que `transcription.transcription_result`
- `segments_gz` (VARBINARY(MAX) NULL)
- `segments_raw_bytes` (INT NULL)
- `segment_count` (INT NULL)
- `created_at` (DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME())
- `station_id` (INT NULL) - copia de `youtube_channel.station_id` al guardar (= `analytics.fuente.fuente_id`)
- `aired_at_local` (DATETIME2(0) NULL) - hora local al aire del bloque: misma regla que `youtube_audio_chunk.aired_at_local` (`virtual_start` o `published_at` en la zona del canal, + `chunk_index * 300 s`)

`station_id` y `aired_at_local` permiten que `analytics.vw_contenido` (TranscriptApi) trate estos bloques como una transcripción más: el detector de IA y los reportes los procesan igual que los de Whisper.
- `UQ (video_id, chunk_index)`


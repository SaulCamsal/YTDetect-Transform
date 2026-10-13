-- =====================================================================
-- MonitorTranscription DB: Agregar columna s3_folder a youtube.youtube_channel
-- Base de datos: MonitorTranscription (SQL Server, AWS RDS)
-- Esquema: youtube
-- =====================================================================

IF NOT EXISTS (
    SELECT 1 
    FROM sys.columns 
    WHERE object_id = OBJECT_ID('youtube.youtube_channel') 
      AND name = 's3_folder'
)
BEGIN
    ALTER TABLE youtube.youtube_channel
    ADD s3_folder VARCHAR(255) NULL;
    
    PRINT 'Columna s3_folder agregada exitosamente a youtube.youtube_channel';
END
ELSE
BEGIN
    PRINT 'La columna s3_folder ya existe en youtube.youtube_channel';
END;

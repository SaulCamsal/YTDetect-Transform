-- =====================================================================
-- MonitorTranscription DB: Limpiar videos y fragmentos de prueba
-- Base de datos: MonitorTranscription (SQL Server)
-- Esquema: youtube
-- =====================================================================

USE MonitorTranscription;
GO

-- 1. Ver qué videos están registrados actualmente antes de borrar
SELECT 
    v.video_id, 
    v.channel_id, 
    c.channel_name, 
    v.title, 
    v.status, 
    v.published_at, 
    v.created_at
FROM youtube.youtube_video v
LEFT JOIN youtube.youtube_channel c ON c.channel_id = v.channel_id
ORDER BY v.created_at DESC;
GO

-- 2. Borrado seguro (respetando Foreign Keys)
-- Primero se eliminan los chunks de audio por la clave foránea fk_youtube_audio_chunk_video
DELETE FROM youtube.youtube_audio_chunk;

-- Luego se eliminan todos los videos
DELETE FROM youtube.youtube_video;

-- 3. (Opcional) Reiniciar los contadores de ID IDENTITY a 0
DBCC CHECKIDENT ('youtube.youtube_audio_chunk', RESEED, 0);
DBCC CHECKIDENT ('youtube.youtube_video', RESEED, 0);
GO

-- 4. Verificación posterior al borrado
SELECT COUNT(*) AS total_videos FROM youtube.youtube_video;
SELECT COUNT(*) AS total_chunks FROM youtube.youtube_audio_chunk;
GO

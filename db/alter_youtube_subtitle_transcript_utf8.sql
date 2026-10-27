-- =====================================================================
-- MonitorTranscription DB: transcript_text de subtítulos en UTF-8
--
-- Deja youtube.youtube_subtitle_transcript.transcript_text con la misma
-- collation que transcription.transcription_result.transcript_text
-- (Latin1_General_100_CI_AI_SC_UTF8). Con la collation default de la BD
-- (SQL_Latin1_General_CP1_CI_AS, página 1252) se pierden como '?' los
-- caracteres fuera de Latin-1: emojis, ♪ de los subtítulos automáticos, etc.
--
-- - Requiere SQL Server 2019+ (igual que transcription_result).
-- - No requiere cambios en el worker: el driver JDBC manda los textos como
--   NVARCHAR (sendStringParametersAsUnicode=true por default) y Hibernate
--   (ddl-auto: validate) no valida la collation.
-- - Lo que ya se guardó como '?' no se recupera; aplica a lo nuevo.
-- - Reescribe la columna (cambia de página de códigos) y toma un bloqueo
--   de esquema mientras corre: correrlo en horario de poca carga.
-- - Idempotente: si la columna ya está en UTF-8 no hace nada.
-- =====================================================================
USE MonitorTranscription;
GO

DECLARE @actual sysname = (
    SELECT c.collation_name
    FROM   sys.columns c
    WHERE  c.object_id = OBJECT_ID('youtube.youtube_subtitle_transcript')
      AND  c.name = 'transcript_text');

IF @actual IS NULL
    THROW 50041, 'No existe youtube.youtube_subtitle_transcript.transcript_text', 1;

IF @actual = 'Latin1_General_100_CI_AI_SC_UTF8'
    PRINT 'transcript_text ya está en Latin1_General_100_CI_AI_SC_UTF8; no se hace nada.';
ELSE
BEGIN
    PRINT CONCAT('Cambiando collation de transcript_text: ', @actual, ' -> Latin1_General_100_CI_AI_SC_UTF8');
    ALTER TABLE youtube.youtube_subtitle_transcript
        ALTER COLUMN transcript_text VARCHAR(MAX) COLLATE Latin1_General_100_CI_AI_SC_UTF8 NULL;
END
GO

-- Comprobación: debe decir Latin1_General_100_CI_AI_SC_UTF8 en las dos tablas
SELECT  OBJECT_SCHEMA_NAME(c.object_id) + '.' + OBJECT_NAME(c.object_id) AS tabla,
        c.name AS columna,
        c.collation_name
FROM    sys.columns c
WHERE   c.name = 'transcript_text'
  AND   c.object_id IN (OBJECT_ID('youtube.youtube_subtitle_transcript'),
                        OBJECT_ID('transcription.transcription_result'));
GO

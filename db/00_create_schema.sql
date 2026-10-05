-- =====================================================================
-- Paso 1 (humano, SSMS): crear el schema "youtube" en MonitorTranscription.
-- Ejecutar ANTES de V1__youtube_schema.sql
-- =====================================================================
USE MonitorTranscription;
GO

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'youtube')
    EXEC('CREATE SCHEMA youtube AUTHORIZATION dbo');
GO

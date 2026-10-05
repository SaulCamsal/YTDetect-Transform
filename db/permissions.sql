-- =====================================================================
-- Paso 3 (humano / DBA, SSMS): permisos del usuario de la aplicación.
-- Ajusta el nombre del login/usuario a tu entorno.
--
-- Modelo:
--   schema youtube        -> lectura y escritura de filas (permanente)
--   schema transcription  -> solo lectura
--   DDL (CREATE/ALTER)    -> NO lo necesita la app; solo quien ejecuta V1 en SSMS
-- =====================================================================
USE MonitorTranscription;
GO

-- CREATE USER yt_app FOR LOGIN yt_app;   -- descomentar si el usuario aún no existe
GO

GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::youtube TO yt_app;
GRANT SELECT ON SCHEMA::transcription TO yt_app;          -- opcional (diagnóstico / cruces)
DENY  INSERT, UPDATE, DELETE ON SCHEMA::transcription TO yt_app;
GO

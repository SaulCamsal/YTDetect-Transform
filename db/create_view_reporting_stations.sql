-- ===================================================================
-- Script: create_view_reporting_stations.sql
-- Propósito: Vista opcional en MonitorTranscription que mapea
--            las estaciones de YouTube de la BD Reporting.
-- Requisito: Que ambas bases de datos residan en la misma instancia de SQL Server.
-- ===================================================================

USE MonitorTranscription;
GO

CREATE OR ALTER VIEW youtube.vw_reporting_youtube_stations AS
SELECT 
    si.stream_id,
    si.stream_desc,
    si.stream_owner,
    f.Folder AS s3_folder
FROM Reporting.dbo.vw_Station_Spots si
INNER JOIN Reporting.dbo.stationfolders f ON f.stream_id = si.stream_id
WHERE si.id_country = 27
  AND si.stream_owner IS NOT NULL
  AND si.stream_owner LIKE '%youtube%';
GO

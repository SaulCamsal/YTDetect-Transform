-- =====================================================================
-- MonitorTranscription DB: Alta de canales de YouTube en transcription.station_config
-- Base de datos: MonitorTranscription (SQL Server)
-- Esquema: transcription
-- =====================================================================

USE MonitorTranscription;
GO

MERGE transcription.station_config AS target
USING (VALUES
    (6321, 'CHART/YT/MILENIO/', 'milenio', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))),
    (6322, 'CHART/YT/GRUPOFORMULA-RF/', 'GrupoFormula-RF', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))),
    (6323, 'CHART/YT/ARISTEGUINOTICIAS/', 'AristeguiNoticias', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))),
    (6324, 'CHART/YT/FINANCIEROMEXICO/', 'FinancieroMexico', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))),
    (6325, 'CHART/YT/GRUPOANIMALTV/', 'GrupoAnimalTV', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))),
    (6326, 'CHART/YT/LATINUSUS/', 'Latinusus', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))),
    (6327, 'CHART/YT/GOBIERNODEMEXICO/', 'gobiernodemexico', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255))),
    (6328, 'CHART/YT/CAMARADEDIPUTADOSMX/', 'camaradediputadosmx', CAST(NULL AS VARCHAR(50)), 1, 1, 5, 0, CAST(NULL AS VARCHAR(255)))
) AS src (station_id, s3_prefix, station_name, timezone, enabled, process_weekends, priority, run_ai, notes)
ON target.station_id = src.station_id
WHEN MATCHED THEN
    UPDATE SET
        s3_prefix = src.s3_prefix,
        station_name = src.station_name,
        timezone = src.timezone,
        enabled = src.enabled,
        process_weekends = src.process_weekends,
        priority = src.priority,
        run_ai = src.run_ai,
        notes = src.notes,
        updated_at = GETUTCDATE()
WHEN NOT MATCHED THEN
    INSERT (station_id, s3_prefix, station_name, timezone, enabled, process_weekends, priority, run_ai, notes, created_at, updated_at)
    VALUES (src.station_id, src.s3_prefix, src.station_name, src.timezone, src.enabled, src.process_weekends, src.priority, src.run_ai, src.notes, GETUTCDATE(), GETUTCDATE());
GO

-- Verificación de registros cargados
SELECT station_id, s3_prefix, station_name, timezone, enabled, priority, run_ai, notes, created_at, updated_at
FROM transcription.station_config
WHERE station_id BETWEEN 6321 AND 6328
ORDER BY station_id;
GO

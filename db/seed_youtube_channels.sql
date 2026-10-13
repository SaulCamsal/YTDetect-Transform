-- =====================================================================
-- MonitorTranscription DB: Carga inicial de los 8 canales de YouTube
-- Base de datos: MonitorTranscription (SQL Server, AWS RDS)
-- Esquema: youtube
-- =====================================================================

MERGE youtube.youtube_channel AS target
USING (VALUES
    ('UCFxHplbcoJK9m70c4VyTIxg', N'MILENIO', 'UUFxHplbcoJK9m70c4VyTIxg', 6321, '/CHART/YT/MILENIO', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION'),
    ('UCzmd9Aj2wmPggWZJpLdBB8Q', N'Grupo Fórmula', 'UUzmd9Aj2wmPggWZJpLdBB8Q', 6322, '/CHART/YT/GRUPOFORMULA-RF', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION'),
    ('UCafNntv_qTQB26AvbEQNHPw', N'Aristegui Noticias', 'UUafNntv_qTQB26AvbEQNHPw', 6323, '/CHART/YT/ARISTEGUINOTICIAS', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION'),
    ('UCHEZcUuC5ulaAFDq1LDqdTw', N'El Financiero Bloomberg', 'UUHEZcUuC5ulaAFDq1LDqdTw', 6324, '/CHART/YT/FINANCIEROMEXICO', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION'),
    ('UCwUr2RA-8YxAB5COLWTLhOw', N'Animal Político', 'UUwUr2RA-8YxAB5COLWTLhOw', 6325, '/CHART/YT/GRUPOANIMALTV', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION'),
    ('UC-FVhfqCwhzpJ4DTJOMMofA', N'Latinus_us', 'UU-FVhfqCwhzpJ4DTJOMMofA', 6326, '/CHART/YT/LATINUSUS', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION'),
    ('UCvzHrtf9by1-UY67SfZse8w', N'Gobierno de México', 'UUvzHrtf9by1-UY67SfZse8w', 6327, '/CHART/YT/GOBIERNODEMEXICO', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION'),
    ('UCkLEVSsqQSCZ8pnyiLyGn3w', N'Cámara de Diputados', 'UUkLEVSsqQSCZ8pnyiLyGn3w', 6328, '/CHART/YT/CAMARADEDIPUTADOSMX', 'America/Mexico_City', 'es', 1, 'SUBSCRIPTION')
) AS src (channel_id, channel_name, uploads_playlist_id, station_id, s3_folder, timezone, language_code, enabled, origin)
ON target.channel_id = src.channel_id
WHEN MATCHED THEN
    UPDATE SET
        channel_name = src.channel_name,
        uploads_playlist_id = src.uploads_playlist_id,
        station_id = src.station_id,
        s3_folder = src.s3_folder,
        timezone = src.timezone,
        language_code = src.language_code,
        enabled = src.enabled,
        updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (channel_id, channel_name, uploads_playlist_id, station_id, s3_folder, timezone, language_code, enabled, origin, created_at, version)
    VALUES (src.channel_id, src.channel_name, src.uploads_playlist_id, src.station_id, src.s3_folder, src.timezone, src.language_code, src.enabled, src.origin, SYSUTCDATETIME(), 0);

-- Verificación de canales insertados/actualizados
SELECT id, channel_id, channel_name, station_id, s3_folder, uploads_playlist_id, enabled
FROM youtube.youtube_channel
ORDER BY station_id;

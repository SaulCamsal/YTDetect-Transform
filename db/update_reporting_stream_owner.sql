-- =====================================================================
-- Reporting DB: Actualización de stream_owner con los links de YouTube
-- Base de datos: Reporting
-- Tablas: station_info_postgresql, vw_Station_Spots
-- =====================================================================

-- 1. Actualización en la tabla base station_info_postgresql
UPDATE station_info_postgresql
SET stream_owner = CASE stream_id
    WHEN 6321 THEN 'https://www.youtube.com/@milenio'
    WHEN 6322 THEN 'https://www.youtube.com/@GrupoFormula-RF'
    WHEN 6323 THEN 'https://www.youtube.com/@AristeguiNoticias'
    WHEN 6324 THEN 'https://www.youtube.com/@FinancieroMexico'
    WHEN 6325 THEN 'https://www.youtube.com/@GrupoAnimalTV'
    WHEN 6326 THEN 'https://www.youtube.com/@Latinusus'
    WHEN 6327 THEN 'https://www.youtube.com/@gobiernodemexico'
    WHEN 6328 THEN 'https://www.youtube.com/@camaradediputadosmx'
END
WHERE stream_id IN (6321, 6322, 6323, 6324, 6325, 6326, 6327, 6328);

-- 2. Si vw_Station_Spots fuera una tabla física independiente, se actualiza también:
-- (Si es una vista basada en station_info_postgresql, el UPDATE anterior ya la actualiza automáticamente)
/*
UPDATE vw_Station_Spots
SET stream_owner = CASE stream_id
    WHEN 6321 THEN 'https://www.youtube.com/@milenio'
    WHEN 6322 THEN 'https://www.youtube.com/@GrupoFormula-RF'
    WHEN 6323 THEN 'https://www.youtube.com/@AristeguiNoticias'
    WHEN 6324 THEN 'https://www.youtube.com/@FinancieroMexico'
    WHEN 6325 THEN 'https://www.youtube.com/@GrupoAnimalTV'
    WHEN 6326 THEN 'https://www.youtube.com/@Latinusus'
    WHEN 6327 THEN 'https://www.youtube.com/@gobiernodemexico'
    WHEN 6328 THEN 'https://www.youtube.com/@camaradediputadosmx'
END
WHERE stream_id IN (6321, 6322, 6323, 6324, 6325, 6326, 6327, 6328);
*/

-- 3. Consulta de Verificación
SELECT si.stream_id, si.stream_desc, si.stream_owner, f.Folder
FROM vw_Station_Spots si
INNER JOIN stationfolders f ON f.stream_id = si.stream_id
WHERE si.id_country = 27;

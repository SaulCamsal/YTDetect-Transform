# T05 - Sincronización de Estado de Transcripción y Ensamblado de Texto Final

**Módulo**: `yt-api`
**Rama**: `feat/T01-common-module-setup`

## Objetivo
Implementar el cierre del ciclo de transcripción:
1. Cliente HTTP para la API de Transcripción externa (`MonitorTranscription API`) según el contrato `C5-external-apis.md`:
   - Polling de estado de jobs por estación y fecha: `GET /api/v1/stations/{stationId}/transcriptions?date=YYYY-MM-DD&includeText=false`.
   - Consulta de resultado y segmentos de texto por job: `GET /api/v1/transcriptions/{jobId}/result`.
2. Servicio programado `TranscriptionSyncService` con ShedLock:
   - Identifica chunks en estado `QUEUED` o `PROCESSING`.
   - Agrupa por `(stationId, date)` y consulta el estado en lote.
   - Actualiza el estado en `youtube.youtube_audio_chunk` (`COMPLETED`, `DEAD`, `CANCELLED`).
   - Cuando todos los fragmentos de un video están `COMPLETED`, transiciona el video a `VideoStatus.COMPLETED`.
3. Servicio `VideoTranscriptAssemblyService` y endpoint REST:
   - Ensambla el texto de los fragmentos calculando timestamps globales (`chunk.offset_seconds + segment.start`).
   - Expone `GET /api/v1/youtube/videos/{videoId}/transcript` con el texto completo y los segmentos cronológicos.

## Archivos que te pertenecen
- `yt-api/src/main/resources/application.yml` (propiedades de `external.transcription.*` y `yt.transcription-sync.*`)
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/client/TranscriptionClient.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/client/StationTranscriptionItemDto.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/client/TranscriptionJobResultDto.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/client/TranscriptionSegmentDto.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/TranscriptionSyncService.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/VideoTranscriptAssemblyService.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/dto/FullVideoTranscriptResponse.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/dto/TranscriptSegmentResponse.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/controller/VideoController.java` (añadir endpoint `/transcript`)
- Tests en `yt-api/src/test/...`

## Requerimientos
1. **Respeto a C5**: Autenticación con cabecera `X-API-Key` contra `external.transcription.url`.
2. **Consultas eficientes en lote**: No consultar la API externa job por job para el status; agrupar por estación y fecha como especifica el contrato C5 sección 1.2.
3. **Cálculo de timestamps globales**: Los segmentos retornados por Whisper son relativos al inicio de cada chunk (0 a 300 s). Al ensamblar, sumar `offset_seconds` para que el texto final esté sincronizado con el video de YouTube completo.
4. **Pruebas completas**: Mocks con `MockRestServiceServer` y pruebas unitarias de sincronización y ensamblado.

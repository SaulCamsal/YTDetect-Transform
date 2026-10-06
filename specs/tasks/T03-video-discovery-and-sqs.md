# T03 - Detección de Videos y Encolado a SQS

**Módulo**: `yt-api` (y `yt-common` para el DTO compartido)
**Rama**: `feat/T01-common-module-setup`

## Objetivo
Implementar la detección híbrida de nuevos videos de YouTube:
1. Recepción y validación en tiempo real de notificaciones WebSub (`/websub/callback`).
2. Parser Atom XML con validación de firma HMAC SHA-1 / SHA-256 (`X-Hub-Signature`).
3. Polling de respaldo para canales activos (`playlistItems.list`).
4. Servicio `VideoDiscoveryService` para consultar metadatos con `videos.list`, persistir en `youtube.youtube_video` y filtrar directos/duración > 10 h.
5. Servicio `LiveStreamWatcher` para re-evaluar transmisiones en vivo (`WAITING_LIVE`).
6. Servicio `YoutubeDownloadQueueService` con `SqsTemplate` de Spring Cloud AWS para publicar el mensaje `DownloadJobMessage` en `youtube-download-queue`.

## Archivos que te pertenecen
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/dto/DownloadJobMessage.java`
- `yt-api/pom.xml` (añadir dependencia `spring-cloud-aws-starter-sqs`)
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/websub/WebSubService.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/websub/WebSubCallbackController.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/websub/AtomFeedParser.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/websub/HmacSignatureValidator.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/VideoDiscoveryService.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubePollingService.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/LiveStreamWatcher.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubeDownloadQueueService.java`
- Tests en `yt-api/src/test/...`

## Requerimientos
1. **DTO Compartido**:
   - `DownloadJobMessage(String videoId, String channelId, String youtubeUrl)` conforme al contrato C3.
2. **WebSub Hub Callback**:
   - `GET /api/v1/youtube/websub/callback`: devuelve `hub.challenge` y actualiza `websub_status = VERIFIED` y `websub_lease_expires_at`.
   - `POST /api/v1/youtube/websub/callback`: valida `X-Hub-Signature` contra el secreto configurado en `yt.websub.secret`. Parsea el XML y extrae `videoId` y `channelId`.
3. **Polling de Respaldo**:
   - `@Scheduled(fixedDelayString = "${yt.polling.delay:1800000}")`: para cada canal con `enabled = true` y `uploadsPlaylistId != null`, consulta los últimos elementos de `playlistItems.list` y llama a `VideoDiscoveryService`.
4. **Descubrimiento y Reglas de Negocio (`VideoDiscoveryService`)**:
   - Consulta `videos.list` (en lote de hasta 50 IDs) obteniendo duración ISO 8601 (`PT#H#M#S` vía `Duration.parse`), `liveStreamingDetails` y snippet.
   - Si `duration_seconds > 36000` (10 horas) -> marca `status = SKIPPED`.
   - Si `live_status == 'live'` o `'upcoming'` -> marca `status = WAITING_LIVE`.
   - Si es video normal completado -> persiste como `status = QUEUED_DOWNLOAD` y publica inmediatamente en SQS.
   - Atrapa colisiones de clave única `uq_youtube_video_video_id` de forma idempotente para evitar procesar dos veces el mismo video.
5. **LiveStreamWatcher**:
   - Tarea programada (cada 10 min) que re-consulta `videos.list` de los videos en `WAITING_LIVE`. Cuando `actualEndTime` está presente y la duración es <= 10 horas, transiciona a `QUEUED_DOWNLOAD` y publica a SQS.
6. **Encolado SQS**:
   - `YoutubeDownloadQueueService`: usa `io.awspring.cloud.sqs.operations.SqsTemplate` para enviar `DownloadJobMessage` a la cola configurada (`yt.sqs.download-queue: youtube-download-queue`).

## Pruebas
- Pruebas unitarias para `AtomFeedParser` y `HmacSignatureValidator`.
- Pruebas unitarias para `VideoDiscoveryService` (descarte por duración, manejo de directos, encolado a SQS).
- Pruebas de integración web para `WebSubCallbackController`.

# T04 - Módulo yt-worker (Consumidor SQS, Descarga de Audio, Fragmentación y Despacho a Whisper)

**Módulo**: `yt-worker`
**Rama**: `feat/T01-common-module-setup`

## Objetivo
Implementar el servicio consumidor en segundo plano `yt-worker`:
1. Consumo de mensajes `DownloadJobMessage` desde la cola SQS `youtube-download-queue` mediante Spring Cloud AWS.
2. Extracción de audio usando `yt-dlp` (solo audio, sin video) hacia un directorio temporal.
3. Normalización y segmentación de audio en fragmentos de 5 minutos (300 s) a 16 kHz Mono con `ffmpeg`.
4. Subida de fragmentos a Amazon S3 (`s3://{bucket}/youtube/{channelId}/{year}/{month}/{videoId}/chunk_{index}.mp3`).
5. Persistencia de fragmentos en `youtube.youtube_audio_chunk` y actualización de estado en `youtube.youtube_video`.
6. Despacho HTTP de cada fragmento hacia la API externa de transcripción Whisper según el contrato `C5-external-apis.md`.
7. Limpieza de archivos temporales del disco y control de reintentos y errores (`FAILED`).

## Archivos que te pertenecen
- `pom.xml` (raíz: añadir módulo `yt-worker`)
- `yt-worker/pom.xml`
- `yt-worker/src/main/resources/application.yml`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/YtWorkerApplication.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/config/WorkerConfig.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/cli/CliProcessRunner.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/cli/DefaultCliProcessRunner.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/AudioProcessingService.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/S3AudioUploadService.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/client/TranscriptionApiClient.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/client/TranscriptionJobRequest.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/client/TranscriptionJobResponse.java`
- `yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/listener/DownloadJobListener.java`
- Tests en `yt-worker/src/test/...`

## Requerimientos
1. **Módulo ejecutable**: `yt-worker` se ejecuta independientemente de `yt-api`. Utiliza Spring Boot 4 y Spring Cloud AWS 4.2.0.
2. **Abstracción de CLI**: `CliProcessRunner` permite desacoplar los comandos `yt-dlp` y `ffmpeg` para garantizar pruebas unitarias 100% portables en cualquier entorno de CI o desarrollo.
3. **Audio estandarizado**: Fragmentos de máximo 300 segundos, mono, 16,000 Hz, 64 kbps MP3.
4. **S3 y DB**: Registro de fragmentos en `youtube.youtube_audio_chunk` con timestamps locales (`aired_at_local`) calculados en base a la zona horaria del canal.
5. **Integración con Whisper**: Llamada a `POST /api/v1/transcriptions` con autenticación `X-API-Key`.
6. **Manejo de errores y reintentos**: Si `yt-dlp` falla (video privado/eliminado), capturar el código y mensaje de error y registrarlo en `last_error_code` y `last_error_message`.

## Pruebas
- Pruebas unitarias para `AudioProcessingService`.
- Pruebas unitarias para `TranscriptionApiClient`.
- Pruebas unitarias para `S3AudioUploadService` y `DownloadJobListener`.

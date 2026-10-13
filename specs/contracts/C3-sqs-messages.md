# C3 - Mensajes en Cola SQS (youtube-download-queue)

Este contrato define el payload que publica `yt-api` y consume `yt-worker` a través de Amazon SQS.

## Cola: `youtube-download-queue`
- Formato de serialización: JSON UTF-8
- Atributos estándar: no requiere headers adicionales obligatorios.
- Deduplicación / Visibilidad: La visibilidad predeterminada de la cola es de 30 minutos (1800 s), ampliada mediante `ChangeMessageVisibility` en el worker para videos extensos (hasta 10 horas).

## Formato del Mensaje
```json
{
  "videoId": "dQw4w9WgXcQ",
  "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
  "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```

### Campos:
- `videoId` (String, obligatorio): ID del video de YouTube (ej. 11 caracteres estándar).
- `channelId` (String, obligatorio): ID del canal de YouTube asociado.
- `youtubeUrl` (String, obligatorio): URL canónica para descarga con `yt-dlp`.

## Record Java Compartido (`yt-common`)
Ubicación: `com.monitorlatino.ytdetect.common.dto.DownloadJobMessage`:
```java
public record DownloadJobMessage(
    String videoId,
    String channelId,
    String youtubeUrl
) {}
```

## 2. Cola: `audio-arrivals` (con Dead-Letter Queue: `audio-arrivals-dlq`)
- Propósito: Cola compartida del ecosistema MonitorLatino donde `yt-worker` notifica la llegada de audios de 5 minutos normalizados y subidos a S3 para su transcripción asíncrona por parte de los workers de Whisper.
- Formato de serialización: JSON UTF-8.

### Formato del Mensaje (`AudioArrivalMessage`)
```json
{
  "jobId": "f369bb48-3f42-4534-b045-84575f5769fb",
  "stationId": 6321,
  "audioUrl": "s3://monitorlatino/CHART/YT/MILENIO/20261014/20261014_120000.mp3",
  "fileName": "20261014_120000.mp3",
  "airedAtLocal": "2026-10-14T12:00:00",
  "timezone": "America/Mexico_City",
  "language": "es",
  "vad": true,
  "sourceSystem": "youtube-ingest"
}
```

### Campos:
- `jobId` (UUID, opcional): Identificador único del trabajo generado para rastreo.
- `stationId` (Integer, obligatorio): ID de la estación/canal en MonitorLatino (stream_id, ej. 6321).
- `audioUrl` (String, obligatorio): URL S3 completa (`s3://<bucket>/<Folder>/<YYYYMMDD>/<YYYYMMDD_HHMMSS>.mp3`).
- `fileName` (String, opcional): Nombre del archivo MP3 (`YYYYMMDD_HHMMSS.mp3`).
- `airedAtLocal` (String / LocalDateTime, opcional): Hora local al aire en formato ISO-8601.
- `timezone` (String, opcional): Zona horaria de la emisora (default: `America/Mexico_City`).
- `language` (String, opcional): Idioma esperado (default: `es`).
- `vad` (Boolean, opcional): Voice Activity Detection activado (default: `true`).
- `sourceSystem` (String, opcional): Identificador del emisor (default: `youtube-ingest`).

### Record Java Compartido (`yt-common`)
Ubicación: `com.monitorlatino.ytdetect.common.dto.AudioArrivalMessage`
```java
public record AudioArrivalMessage(
    UUID jobId,
    Integer stationId,
    String audioUrl,
    String fileName,
    String airedAtLocal,
    String timezone,
    String language,
    Boolean vad,
    String sourceSystem
) {}
```

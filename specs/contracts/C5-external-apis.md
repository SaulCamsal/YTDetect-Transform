# C5 - APIs Externas

Este contrato define las interacciones con APIs fuera del alcance de este proyecto, que **no podemos modificar**, y cuyas firmas y endpoints son estrictamente fijas.

## 1. API de Transcripción (MonitorTranscription API)
URL base: `https://transcript.monitorlatino.com/api/v1` (o lo que dictamine `application.yml` bajo `external.transcription.url`).
Autenticación: Header `X-API-Key` (inyección desde Secrets Manager).

### 1.1 Enviar trabajo de transcripción (Worker)
`POST /api/v1/transcriptions`

**Request Body (JSON):**
```json
{
  "stationId": 1234,
  "audioUrl": "s3://monitor-youtube-audio/youtube/UC123/2026/09/abc/20260901_120000.mp3",
  "timezone": "America/Mexico_City",
  "language": "es",
  "vad": true,
  "sourceSystem": "youtube-ingest"
}
```
**Response (200 OK):**
```json
{
  "jobId": "uuid-here",
  "duplicate": false
}
```

### 1.2 Polling de status (yt-api)
`GET /api/v1/stations/{stationId}/transcriptions?date=YYYY-MM-DD&includeText=false`

Retorna todos los jobs de una estación y día (basado en `aired_at_local`). Usado para chequear el `transcription_status`. Status esperados: `QUEUED`, `PROCESSING`, `COMPLETED`, `DEAD`, `CANCELLED`.

### 1.3 Ensamblado de texto (yt-api)
`GET /api/v1/transcriptions/{jobId}/result`

**Response:**
```json
{
  "jobId": "...",
  "status": "COMPLETED",
  "segments": [
    { "start": 0.0, "end": 5.0, "text": "Hola mundo" }
  ]
}
```

## 2. YouTube Data API v3
Manejada a través de `google-api-services-youtube` y `google-auth-library`.
OAuth Client Credentials + API Key pública según convenga.

Endpoints que usaremos (conceptual, la SDK mapea esto):
- `subscriptions.list?mine=true`
- `channels.list?id=...`
- `playlistItems.list`
- `videos.list?id=...&part=contentDetails,liveStreamingDetails`

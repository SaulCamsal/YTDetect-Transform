# C2 - Endpoints REST de YTDetect (Módulo `yt-api`)

Todos los endpoints (salvo el callback público de WebSub) se agrupan bajo `/api/v1/youtube` y requieren autenticación mediante cabecera HTTP `X-API-Key`.

## 1. Endpoints de Canales (`/api/v1/youtube/channels`)

### 1.1 Registrar canal manualmente
`POST /api/v1/youtube/channels`

**Request Body:**
```json
{
  "channelId": "UC_x5XG1OV2P6uZZ5FSM9Ttw",
  "channelName": "Google Developers",
  "stationId": 105,
  "timezone": "America/Mexico_City",
  "languageCode": "es"
}
```
*Campos obligatorios:* `channelId`.
*Por defecto:* `origin = MANUAL`, `enabled = false` (o `true` si se asigna `stationId`), `timezone = America/Mexico_City`, `languageCode = es`.

**Response (201 Created):**
```json
{
  "id": 1,
  "channelId": "UC_x5XG1OV2P6uZZ5FSM9Ttw",
  "channelName": "Google Developers",
  "uploadsPlaylistId": "UU_x5XG1OV2P6uZZ5FSM9Ttw",
  "stationId": 105,
  "timezone": "America/Mexico_City",
  "languageCode": "es",
  "enabled": false,
  "origin": "MANUAL",
  "createdAt": "2026-10-05T12:00:00Z"
}
```

### 1.2 Listar canales
`GET /api/v1/youtube/channels?page=0&size=20&enabled=true`

**Response (200 OK):**
```json
{
  "content": [ ... ],
  "totalElements": 50,
  "totalPages": 3,
  "pageNumber": 0,
  "pageSize": 20
}
```

### 1.3 Actualizar canal
`PATCH /api/v1/youtube/channels/{id}`

**Request Body:**
```json
{
  "enabled": true,
  "stationId": 105,
  "timezone": "America/Mexico_City",
  "languageCode": "es"
}
```
*Campos opcionales (solo actualiza los campos presentes).*

**Response (200 OK):** Objeto de canal actualizado.

### 1.4 Forzar sincronización de suscripciones
`POST /api/v1/youtube/channels/sync`

Dispara de forma asíncrona o sincrónica la lectura de `subscriptions.list?mine=true` e importa canales nuevos.

**Response (200 OK):**
```json
{
  "status": "COMPLETED",
  "discoveredCount": 15,
  "newlyAddedCount": 2,
  "updatedCount": 13
}
```

## 2. Endpoints de Videos (`/api/v1/youtube/videos`)

### 2.1 Listar videos detectados
`GET /api/v1/youtube/videos?channelId=UC_xxx&status=QUEUED_DOWNLOAD&page=0&size=20`

*Filtros opcionales:*
- `channelId`: ID de canal de YouTube.
- `status`: Estado del video (`QUEUED_DOWNLOAD`, `WAITING_LIVE`, `SKIPPED`, `DOWNLOADING`, `COMPLETED`, etc.).
- `page`, `size`: Paginación.

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "videoId": "abc123xyz",
      "channelId": "UC_x5XG1OV2P6uZZ5FSM9Ttw",
      "channelName": "Google Developers",
      "title": "Titulo del video",
      "description": "Descripcion...",
      "publishedAt": "2026-10-05T12:00:00Z",
      "durationSeconds": 1800,
      "liveStatus": "NONE",
      "status": "QUEUED_DOWNLOAD",
      "discoveredVia": "WEBSUB",
      "s3Bucket": null,
      "s3Prefix": null,
      "chunkCount": null,
      "virtualStart": null,
      "attemptCount": 0,
      "lastErrorCode": null,
      "lastErrorMessage": null,
      "createdAt": "2026-10-05T12:05:00Z",
      "updatedAt": null
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "pageNumber": 0,
  "pageSize": 20
}
```

### 2.2 Consultar video por videoId
`GET /api/v1/youtube/videos/{videoId}`

**Response (200 OK):** Objeto con la información detallada del video.
**Response (404 Not Found):** `{"error": "Video not found"}` si no existe.

### 2.3 Consultar estado de fragmentos y transcripción
`GET /api/v1/youtube/videos/{videoId}/transcription`

**Response (200 OK):**
```json
{
  "videoId": "abc123xyz",
  "status": "COMPLETED",
  "chunkCount": 6,
  "chunks": [
    {
      "id": 1,
      "chunkIndex": 0,
      "offsetSeconds": 0.0,
      "durationSeconds": 300.0,
      "airedAtLocal": "2026-10-05T12:00:00",
      "s3Key": "youtube/UC123/2026/10/abc123xyz/chunk_0000.mp3",
      "transcriptionJobId": "a1b2c3d4-...",
      "transcriptionStatus": "COMPLETED",
      "errorCode": null,
      "errorMessage": null
    }
  ]
}
```


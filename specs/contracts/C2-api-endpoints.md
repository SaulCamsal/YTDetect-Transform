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

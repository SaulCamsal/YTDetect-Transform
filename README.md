# YTDetect-Transform — Ingesta y Transcripción Automatizada de YouTube

Servicio backend distribuido en **Java 21** y **Spring Boot 4.x** diseñado para monitorear canales de YouTube (videos, directos y shorts), extraer únicamente el flujo de audio, fragmentarlo en bloques de 5 minutos, subirlo a Amazon S3 y despacharlo a la API de transcripción existente basada en **Whisper**.

El diseño del proyecto se rige por **Spec-Driven Development (SDD)**, asegurando compatibilidad total con la infraestructura actual de Whisper y garantizando alta resiliencia y procesamiento asíncrono.

---

## Índice

1. [Arquitectura del Sistema](#arquitectura-del-sistema)
2. [Mapeo del Plan de Implementación vs Código](#mapeo-del-plan-de-implementación-vs-código)
3. [Estructura del Repositorio](#estructura-del-repositorio)
4. [Requisitos Previos](#requisitos-previos)
5. [Guía de Instalación Paso a Paso](#guía-de-instalación-paso-a-paso)
6. [Configuración de Variables de Entorno (.env)](#configuración-de-variables-de-entorno-env)
7. [Base de Datos (SQL Server)](#base-de-datos-sql-server)
8. [Ejecución en Entorno Local](#ejecución-en-entorno-local)
9. [Guía de Endpoints REST (API)](#guía-de-endpoints-rest-api)
10. [Flujo Operativo de Detección y Procesamiento](#flujo-operativo-de-detección-y-procesamiento)

---

## Arquitectura del Sistema

```text
Canales de YouTube (Videos / Directos / Shorts)
       │
       ├───> [Push en Tiempo Real] ──> WebSub Hub (PubSubHubbub) ──> WebSubCallbackController (yt-api)
       │
       └───> [Respaldo Periódico]  ──> YouTube Data API v3       ──> YoutubePollingService (yt-api)
                                                                            │
                                                                            ▼
                                                                 VideoDiscoveryService
                                                                (Desduplicación en SQL Server)
                                                                            │
                                                                            ▼
                                                            Cola AWS SQS: youtube-download-queue
                                                                            │
                                                                            ▼
                                                            DownloadJobListener (yt-worker)
                                                                            │
                                                 ┌──────────────────────────┴──────────────────────────┐
                                                 ▼                                                     ▼
                                            yt-dlp CLI                                            ffmpeg CLI
                                    (Descarga solo mejor audio)                           (Normaliza 16kHz mono 64kbps
                                                                                         y segmenta en bloques de 300s)
                                                                                                       │
                                                                                                       ▼
                                                                                           S3AudioUploadService
                                                                                         (Subida de chunks a S3)
                                                                                                       │
                                                                                                       ▼
                                                                                            TranscriptionApiClient
                                                                                       (POST /api/v1/transcriptions)
                                                                                                       │
                                                                                                       ▼
                                                                                            Whisper GPU Workers
                                                                                                       │
                                                                                                       ▼
                                           TranscriptionSyncService (yt-api) <─────────── API de Whisper
                                      (Polling programado con ShedLock)
                                                       │
                                                       ▼
                                      VideoTranscriptAssemblyService (yt-api)
                                   (Ensamblado con timestamps globales continuos)
                                                       │
                                                       ▼
                                         GET /api/v1/youtube/videos/{id}/transcript
```

---

## Mapeo del Plan de Implementación vs Código

Este proyecto implementa íntegramente lo requerido en [`plan_desarrollo_youtube_transcripcion.md`](file:///Context%20Docs/plan_desarrollo_youtube_transcripcion.md). A continuación se detalla qué componente del código es responsable de cada etapa del plan:

| Etapa del Plan Original | Responsabilidad Técnica | Componente / Clase en el Proyecto | Módulo |
| :--- | :--- | :--- | :--- |
| **Principio Principal** | Reutilizar Whisper sin cambios; Whisper solo recibe audio fragmentado y metadatos estándar (`stationId`, `audioUrl`). | [`TranscriptionApiClient`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/client/TranscriptionApiClient.java) | `yt-worker` |
| **Fase 1: Modelo de Datos** | Entidades JPA y repositorios para canales, videos y fragmentos con bloqueo optimista y concurrencia. | [`YoutubeChannel`](file:///yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/entity/YoutubeChannel.java), [`YoutubeVideo`](file:///yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/entity/YoutubeVideo.java), [`YoutubeAudioChunk`](file:///yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/entity/YoutubeAudioChunk.java) | `yt-common` |
| **Fase 2: Catálogo de Canales** | Sincronización automática de suscripciones de YouTube con OAuth 2.0 y administración CRUD REST. | [`YoutubeApiClient`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/client/YoutubeApiClient.java), [`YoutubeChannelService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubeChannelService.java), [`ChannelController`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/controller/ChannelController.java) | `yt-api` |
| **Fase 3: Detección Push (WebSub)** | Notificaciones push de subidas de videos vía PubSubHubbub con validación de firmas HMAC-SHA1/SHA256 y parseo Atom. | [`WebSubCallbackController`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/websub/WebSubCallbackController.java), [`HmacSignatureValidator`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/websub/HmacSignatureValidator.java), [`AtomFeedParser`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/websub/AtomFeedParser.java) | `yt-api` |
| **Fase 3: Polling de Respaldo** | Scheduler distribuido (ShedLock) cada 30 min para escanear playlists de uploads (`UU...`) y watcher de directos. | [`YoutubePollingService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubePollingService.java), [`LiveStreamWatcher`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/LiveStreamWatcher.java) | `yt-api` |
| **Fase 3: Desduplicación y Encolado** | Verificación de `video_id`, validación de duración máxima (10 horas) y publicación en cola de descarga. | [`VideoDiscoveryService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/VideoDiscoveryService.java), [`YoutubeDownloadQueueService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubeDownloadQueueService.java) | `yt-api` |
| **Fase 4: Cola SQS** | Formato estandarizado del mensaje de descarga y cola de trabajo asíncrona independiente. | [`DownloadJobMessage`](file:///yt-common/src/main/java/com/monitorlatino/ytdetect/common/dto/DownloadJobMessage.java) (`youtube-download-queue`) | `yt-common` |
| **Fase 5: Extracción de Audio** | Descarga únicamente del flujo de audio (`-f bestaudio -x --audio-format mp3`) con aislamiento de procesos. | [`AudioProcessingService`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/AudioProcessingService.java), [`CliProcessRunner`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/cli/CliProcessRunner.java) (`yt-dlp`) | `yt-worker` |
| **Fase 5: Normalización y Fragmentación** | Conversión a Mono, 16 kHz, 64 kbps MP3 y división en fragmentos de 5 minutos (300 s) con reset de timestamps. | [`AudioProcessingService`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/AudioProcessingService.java) (`ffmpeg -segment_time 300`) | `yt-worker` |
| **Fase 6: Almacenamiento en S3** | Subida de chunks con convención: `youtube/{channelId}/{year}/{month}/{videoId}/chunk_{index}.mp3`. | [`S3AudioUploadService`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/S3AudioUploadService.java) (`S3Template`) | `yt-worker` |
| **Fase 7: Despacho a Whisper** | Envío de cada chunk listo (`POST /api/v1/transcriptions`) a la API de transcripción y guardado de `jobId`. | [`TranscriptionApiClient`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/client/TranscriptionApiClient.java), [`DownloadJobListener`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/listener/DownloadJobListener.java) | `yt-worker` |
| **Fase 8: Sincronización Whisper** | Sondeo periódico del estado de cada chunk (`COMPLETED`/`FAILED`), actualización y cambio de estado del video. | [`TranscriptionSyncService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/TranscriptionSyncService.java), [`TranscriptionClient`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/client/TranscriptionClient.java) | `yt-api` |
| **Fase 8: Ensamblado y Timestamps** | Alineación temporal de segmentos de audio a la línea global del video (`chunk.offset + segment.start`) y texto final. | [`VideoTranscriptAssemblyService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/VideoTranscriptAssemblyService.java), [`VideoController`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/controller/VideoController.java) | `yt-api` |
| **Fase 9: Resiliencia y Retries** | Manejo de errores de descarga/procesamiento, contador de intentos (`attempt_count`), estados `FAILED` y limpieza de temporales. | [`DownloadJobListener`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/listener/DownloadJobListener.java) | `yt-worker` |

---

## Estructura del Repositorio

El proyecto utiliza una arquitectura multi-módulo de Maven:

```text
YTDetect-Transform/
├── pom.xml                               # POM raíz (Reactor y dependencias padre)
├── .env.example                          # Plantilla de variables de entorno
├── db/                                   # Scripts DDL para SQL Server
│   ├── 00_create_schema.sql              # Creación del esquema 'youtube'
│   ├── V1__youtube_schema.sql            # Tablas e índices del contrato C1
│   └── permissions.sql                   # Concesión de permisos para usuarios de BD
├── specs/                                # Especificaciones y Contratos SDD
│   ├── 00-constitution.md               # Reglas maestras del proyecto
│   ├── contracts/                       # Contratos inmutables (C1 a C5)
│   ├── tasks/                           # Tareas de implementación (T01 a T05)
│   └── handoff/                         # Evidencias de entrega por tarea
├── yt-common/                            # Módulo común (Entidades, Repositorios, DTOs)
│   └── src/main/java/.../common/
│       ├── domain/entity/                # YoutubeChannel, YoutubeVideo, YoutubeAudioChunk
│       ├── domain/repository/            # Spring Data JPA Repositories
│       └── dto/                          # DownloadJobMessage, DTOs de dominio
├── yt-api/                               # Módulo ejecutable: API REST y Detección
│   └── src/main/java/.../api/
│       ├── client/                       # YoutubeApiClient, TranscriptionClient
│       ├── controller/                   # ChannelController, VideoController
│       ├── service/                      # VideoDiscoveryService, YoutubePollingService, etc.
│       └── websub/                       # WebSubCallbackController, AtomFeedParser
└── yt-worker/                            # Módulo ejecutable: Consumidor SQS y Descarga
    └── src/main/java/.../worker/
        ├── cli/                          # CliProcessRunner (yt-dlp y ffmpeg)
        ├── client/                       # TranscriptionApiClient (Whisper)
        ├── listener/                     # DownloadJobListener (@SqsListener)
        └── service/                      # AudioProcessingService, S3AudioUploadService
```

---

## Requisitos Previos

Antes de instalar y correr el proyecto, asegúrate de tener instalado:

1. **Java Development Kit (JDK) 21** o superior.
2. **Maven Wrapper**: Incluido en el repositorio (`.\mvnw.cmd` en Windows, `./mvnw` en Linux/macOS).
3. **Microsoft SQL Server**: Versión 2019 o superior (Local, Docker o AWS RDS).
4. **Herramientas de CLI (requeridas por `yt-worker`):**
   - **yt-dlp**: Para extraer el audio de YouTube.
   - **ffmpeg**: Para normalizar y cortar los fragmentos de audio.

### Instalación de `yt-dlp` y `ffmpeg`:
- **Windows (PowerShell):**
  ```powershell
  winget install yt-dlp
  winget install Gyan.FFmpeg
  ```
- **Linux (Ubuntu/Debian):**
  ```bash
  sudo apt-get update && sudo apt-get install -y ffmpeg
  sudo curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
  sudo chmod a+rx /usr/local/bin/yt-dlp
  ```
- **macOS:**
  ```bash
  brew install yt-dlp ffmpeg
  ```

---

## Guía de Instalación Paso a Paso

### 1. Clonar el repositorio
```bash
git clone https://github.com/SaulCamsal/YTDetect-Transform.git
cd YTDetect-Transform
```

### 2. Configurar el archivo de entorno (`.env`)
Copia la plantilla `.env.example` y crea tu archivo `.env`:
```powershell
# En Windows PowerShell
Copy-Item .env.example .env

# En Linux / macOS
cp .env.example .env
```
Edita `.env` con tus credenciales (ver detalles en la sección siguiente).

### 3. Crear las tablas en la Base de Datos
Ejecuta los scripts ubicados en la carpeta `db/` en tu servidor SQL Server (en la base de datos configurada, por ejemplo `MonitorTranscription`):
1. `db/00_create_schema.sql` (Crea el esquema `youtube`).
2. `db/V1__youtube_schema.sql` (Crea las tablas `youtube_channel`, `youtube_video`, `youtube_audio_chunk`, `shedlock` e índices).
3. `db/permissions.sql` (Otorga permisos de lectura/escritura al usuario de aplicación).

### 4. Compilar y verificar el proyecto
Ejecuta la suite de pruebas unitarias y de integración para validar la correcta configuración:

- **Windows (PowerShell):**
  ```powershell
  .\mvnw.cmd clean verify
  ```
- **Linux / macOS:**
  ```bash
  ./mvnw clean verify
  ```

### 5. Instalar los módulos en el repositorio local
Para que los módulos ejecutables resuelvan el módulo compartido `yt-common`:
```powershell
.\mvnw.cmd install -DskipTests
```

---

## Configuración de Variables de Entorno (.env)

El archivo `.env` controla los secretos y parámetros de conexión:

```env
# ===================================================================
# 1. Base de Datos SQL Server
# ===================================================================
SPRING_DATASOURCE_URL=jdbc:sqlserver://tu-servidor-rds.amazonaws.com:1433;databaseName=MonitorTranscription;encrypt=false;trustServerCertificate=false;
SPRING_DATASOURCE_USERNAME=tu_usuario
SPRING_DATASOURCE_PASSWORD=tu_contraseña

# ===================================================================
# 2. Seguridad interna (Cabecera X-API-Key en endpoints REST)
# ===================================================================
YT_API_KEY=default-secret-key

# ===================================================================
# 3. Google YouTube Data API v3
# ===================================================================
# API Key pública para consultar videos, canales y playlists
YOUTUBE_API_KEY=AIzaSyTuClaveDeGoogleCloud...

# Credenciales OAuth 2.0 (Opcionales: solo requeridas para sincronizar suscripciones personales de tu cuenta)
YOUTUBE_CLIENT_ID=
YOUTUBE_CLIENT_SECRET=
YOUTUBE_REFRESH_TOKEN=

# ===================================================================
# 4. WebSub (PubSubHubbub para notificaciones push)
# ===================================================================
# URL pública accesible por Google (en local se usa ngrok o cloudflared)
YT_WEBSUB_CALLBACK_URL=https://tu-dominio-o-ngrok.com/api/v1/youtube/websub/callback
YT_WEBSUB_SECRET=ytdetect-websub-secret

# ===================================================================
# 5. AWS SQS y S3
# ===================================================================
YT_SQS_DOWNLOAD_QUEUE=youtube-download-queue
YT_S3_BUCKET=monitor-youtube-audio
YT_S3_PREFIX=youtube
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# ===================================================================
# 6. API Externa de Whisper (Transcripción)
# ===================================================================
EXTERNAL_TRANSCRIPTION_URL=https://transcript.monitorlatino.com/api/v1
EXTERNAL_TRANSCRIPTION_API_KEY=tu-api-key-de-transcripcion
```

---

## Ejecución en Entorno Local

Puedes ejecutar los dos componentes del sistema de forma independiente:

### 1. Iniciar la API REST y Detector (`yt-api`)

El módulo `yt-api` gestiona los endpoints REST, el webhook de WebSub, el polling de respaldo y la sincronización con Whisper.

**En Windows PowerShell:**
```powershell
# Cargar variables del .env en la sesión
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), "Process")
    }
}

# Ejecutar el jar indicando el puerto deseado
java -jar yt-api/target/yt-api-0.0.1-SNAPSHOT.jar --server.port=8082
```

**En Linux / macOS:**
```bash
export $(grep -v '^#' .env | xargs)
java -jar yt-api/target/yt-api-0.0.1-SNAPSHOT.jar --server.port=8082
```

La API estará lista en `http://localhost:8082`.

---

### 2. Iniciar el Trabajador de Descargas (`yt-worker`)

El módulo `yt-worker` escucha mensajes de la cola SQS, descarga con `yt-dlp`, procesa con `ffmpeg`, sube los fragmentos a Amazon S3 y notifica a Whisper.

```powershell
# Windows PowerShell
java -jar yt-worker/target/yt-worker-0.0.1-SNAPSHOT.jar --server.port=8081
```

```bash
# Linux / macOS
java -jar yt-worker/target/yt-worker-0.0.1-SNAPSHOT.jar --server.port=8081
```

---

## Guía de Endpoints REST (API)

Todos los endpoints (a excepción del webhook público de WebSub) requieren la cabecera:
`X-API-Key: default-secret-key` (o el valor asignado en `YT_API_KEY`).

### 1. Catálogo de Canales

#### A. Registrar un nuevo canal de YouTube
```bash
curl -X POST http://localhost:8082/api/v1/youtube/channels \
  -H "Content-Type: application/json" \
  -H "X-API-Key: default-secret-key" \
  -d '{
    "channelId": "UC_x5XG1OV2P6uZZ5FSM9Ttw",
    "channelName": "Google Developers",
    "timezone": "America/Mexico_City",
    "languageCode": "en"
  }'
```
*El sistema consultará automáticamente a la YouTube Data API v3 para obtener el título oficial y el ID de la playlist de subidas.*

#### B. Listar canales registrados
```bash
curl -X GET "http://localhost:8082/api/v1/youtube/channels?enabled=true" \
  -H "X-API-Key: default-secret-key"
```

#### C. Activar / Desactivar canal
```bash
curl -X PATCH http://localhost:8082/api/v1/youtube/channels/1 \
  -H "Content-Type: application/json" \
  -H "X-API-Key: default-secret-key" \
  -d '{"enabled": true}'
```

#### D. Sincronizar suscripciones desde la cuenta de YouTube (OAuth2)
```bash
curl -X POST http://localhost:8082/api/v1/youtube/channels/sync \
  -H "X-API-Key: default-secret-key"
```

---

### 2. Videos y Transcripciones

#### A. Listar videos detectados (con filtros)
```bash
curl -X GET "http://localhost:8082/api/v1/youtube/videos?channelId=UC_x5XG1OV2P6uZZ5FSM9Ttw&status=QUEUED_DOWNLOAD" \
  -H "X-API-Key: default-secret-key"
```

#### B. Consultar detalle de un video
```bash
curl -X GET http://localhost:8082/api/v1/youtube/videos/lTQHImoeuEY \
  -H "X-API-Key: default-secret-key"
```

#### C. Consultar estado de fragmentos (Chunks)
```bash
curl -X GET http://localhost:8082/api/v1/youtube/videos/lTQHImoeuEY/transcription \
  -H "X-API-Key: default-secret-key"
```

#### D. Obtener la Transcripción Completa Ensamblada (T05)
```bash
curl -X GET http://localhost:8082/api/v1/youtube/videos/lTQHImoeuEY/transcript \
  -H "X-API-Key: default-secret-key"
```
**Respuesta:**
```json
{
  "videoId": "lTQHImoeuEY",
  "title": "Top 3 new model launches at Gemini Audio at Night",
  "status": "COMPLETED",
  "chunkCount": 1,
  "fullText": "Catch up on the highlights from Gemini Audio at Night...",
  "segments": [
    {
      "start": 0.0,
      "end": 4.5,
      "text": "Catch up on the highlights from Gemini Audio at Night"
    }
  ]
}
```

---

### 3. Callback de WebSub (PubSubHubbub)

- **Verificación de suscripción:**
  `GET /api/v1/youtube/websub/callback?hub.mode=subscribe&hub.challenge=12345&hub.topic=...`
- **Recepción de notificación push:**
  `POST /api/v1/youtube/websub/callback` (Validado con cabecera `X-Hub-Signature`).

---

## Flujo Operativo de Detección y Procesamiento

```text
1. DETECCIÓN
   - WebSub recibe notificación instantánea O el Polling consulta la playlist cada 30 min.
   - VideoDiscoveryService valida que el video no exista en SQL Server.
   - Si la duración excede 10 horas, se marca SKIPPED.
   - Si es válido, se inserta en youtube.youtube_video con estado QUEUED_DOWNLOAD.
   - Se publica el mensaje DownloadJobMessage en youtube-download-queue (SQS).

2. DESCARGA Y SEGMENTACIÓN (yt-worker)
   - DownloadJobListener recibe el trabajo y pasa el estado a DOWNLOADING.
   - yt-dlp descarga exclusivamente el stream de audio en un directorio temporal aislado.
   - ffmpeg recodifica a 16 kHz Mono 64kbps MP3 y lo divide en chunks de máximo 300 segundos.

3. SUBIDA A S3 Y DESPACHO
   - Cada chunk se sube a S3 con la clave: youtube/{channelId}/{year}/{month}/{videoId}/chunk_{index}.mp3.
   - Se persiste el fragmento en youtube.youtube_audio_chunk.
   - Se envía la petición HTTP a la API existente de Whisper (POST /api/v1/transcriptions).
   - Se actualiza el video a TRANSCRIBING y se borran los archivos temporales de disco.

4. SINCRONIZACIÓN Y ENSAMBLADO FINAL (yt-api)
   - TranscriptionSyncService consulta periódicamente el estado de cada trabajo en Whisper.
   - Al completarse todos los fragmentos, el video pasa a COMPLETED.
   - VideoTranscriptAssemblyService suma los offsets (chunk_index * 300s) a cada segmento para
     ofrecer la transcripción global sincronizada con la línea de tiempo de YouTube.
```

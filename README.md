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
                                                            youtube.youtube_video
                                                            (status = QUEUED_DOWNLOAD)
                                                                            │
                                                       (Polling periódico cada 30 segundos)
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
                                                                                         (Subida a S3: monitorlatino/
                                                                                  {Folder}/YYYYMMDD/YYYYMMDD_HHMMSS.mp3)
                                                                                                       │
                                                                                                       ▼
                                                                           (Evento automatico S3 ObjectCreated)
                                                                                                       │
                                                                                                       ▼
                                                                                        Pipeline / Workers de Whisper
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
| **Fase 3: Desduplicación y Encolado** | Verificación de `video_id`, validación de duración máxima (10 horas) y registro en BD (`QUEUED_DOWNLOAD`). | [`VideoDiscoveryService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/VideoDiscoveryService.java), [`YoutubeDownloadQueueService`](file:///yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubeDownloadQueueService.java) | `yt-api` |
| **Fase 4: Despacho a Worker** | Despacho asíncrono y distribuido mediante sondeo periódico de trabajos pendientes en base de datos. | [`DownloadJobListener`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/listener/DownloadJobListener.java) (`@Scheduled` en `youtube_video`) | `yt-worker` |
| **Fase 5: Extracción de Audio** | Descarga únicamente del flujo de audio (`-f bestaudio -x --audio-format mp3`) con aislamiento de procesos. | [`AudioProcessingService`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/AudioProcessingService.java), [`CliProcessRunner`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/cli/CliProcessRunner.java) (`yt-dlp`) | `yt-worker` |
| **Fase 5: Normalización y Fragmentación** | Conversión a Mono, 16 kHz, 64 kbps MP3 y división en fragmentos de 5 minutos (300 s) con formato de fecha radio. | [`AudioProcessingService`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/AudioProcessingService.java) (`ffmpeg -segment_time 300`) | `yt-worker` |
| **Fase 6: Almacenamiento en S3** | Subida al bucket `monitorlatino` bajo subcarpeta por estación y fecha: `{Folder}/{YYYYMMDD}/{YYYYMMDD_HHMMSS}.mp3`. | [`S3AudioUploadService`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/S3AudioUploadService.java) (`S3Template`) | `yt-worker` |
| **Fase 7: Ingesta en Transcripción** | Notificación automática nativa de S3 al depositar el audio en la carpeta correspondiente hacia Whisper. | Evento nativo de S3 / [`S3AudioUploadService`](file:///yt-worker/src/main/java/com/monitorlatino/ytdetect/worker/service/S3AudioUploadService.java) | `yt-worker` |
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
│   ├── alter_youtube_channel_add_s3_folder.sql # Agrega columna s3_folder a youtube_channel
│   ├── seed_youtube_channels.sql         # Registro de los 8 canales iniciales vinculados a S3
│   ├── update_reporting_stream_owner.sql # Actualización de stream_owner en BD Reporting
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
│       └── dto/                          # AudioArrivalMessage, DownloadJobMessage, DTOs
├── yt-api/                               # Módulo ejecutable: API REST y Detección
│   └── src/main/java/.../api/
│       ├── client/                       # YoutubeApiClient, TranscriptionClient
│       ├── controller/                   # ChannelController, VideoController
│       ├── service/                      # VideoDiscoveryService, ReportingStationSyncService, etc.
│       └── websub/                       # WebSubCallbackController, AtomFeedParser
└── yt-worker/                            # Módulo ejecutable: Descarga, S3 y Cola de Audios
    └── src/main/java/.../worker/
        ├── cli/                          # CliProcessRunner (yt-dlp y ffmpeg)
        ├── client/                       # TranscriptionApiClient (Whisper)
        ├── listener/                     # DownloadJobListener (@Scheduled polling en BD)
        └── service/                      # AudioProcessingService, S3AudioUploadService, AudioArrivalQueueService
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
3. `db/alter_youtube_channel_add_s3_folder.sql` (Agrega la columna `s3_folder` en `youtube.youtube_channel`).
4. `db/seed_youtube_channels.sql` (Inserta o actualiza los 8 canales iniciales de YouTube con sus `station_id`, `s3_folder` y playlists `UU...`).
5. `db/update_reporting_stream_owner.sql` (*Opcional/Administración*: Ejecutar en la base de datos `Reporting` para registrar la URL del canal en `stream_owner`).
6. `db/permissions.sql` (Otorga permisos de lectura/escritura al usuario de aplicación).

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
# 1. Base de Datos SQL Server (MonitorTranscription)
# ===================================================================
SPRING_DATASOURCE_URL=jdbc:sqlserver://localhost:1433;databaseName=MonitorTranscription;encrypt=true;trustServerCertificate=true
SPRING_DATASOURCE_USERNAME=sa
SPRING_DATASOURCE_PASSWORD=TuPasswordAqui

# ===================================================================
# 2. Seguridad interna (Cabecera X-API-Key en endpoints REST de yt-api)
# ===================================================================
YT_API_KEY=default-secret-key

# ===================================================================
# 3. Google YouTube Data API v3
# ===================================================================
# Clave publica requerida para resolucion de canales y polling de playlists
YOUTUBE_API_KEY=AIzaSyTuClaveDeGoogleCloud...

# Credenciales OAuth 2.0 (Opcionales: solo requeridas para sincronizar suscripciones personales de tu cuenta)
YOUTUBE_CLIENT_ID=
YOUTUBE_CLIENT_SECRET=
YOUTUBE_REFRESH_TOKEN=

# ===================================================================
# 4. WebSub (PubSubHubbub para notificaciones push en tiempo real)
# ===================================================================
# URL publica accesible por Google (en local se usa ngrok o cloudflared)
YT_WEBSUB_CALLBACK_URL=https://tu-dominio-o-ngrok.com/api/v1/youtube/websub/callback
YT_WEBSUB_SECRET=ytdetect-websub-secret

# ===================================================================
# 5. AWS Credenciales, SQS y S3 (Region us-west-2 Oregon)
# ===================================================================
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_REGION=us-west-2

# Bucket S3 existente de produccion y prefijo base
# (El bucket ya cuenta con eventos automaticos que notifican a Whisper al subir los chunks)
YT_S3_BUCKET=monitorlatino
YT_S3_PREFIX=CHARTS

# (Opcional) Forzar envio manual a SQS desde la app (por defecto desactivado: false)
# YT_SQS_ENABLED=false

# ===================================================================
# 6. API Externa de Whisper (Transcripción)
# ===================================================================
EXTERNAL_TRANSCRIPTION_URL=https://transcript.monitorlatino.com/api/v1
EXTERNAL_TRANSCRIPTION_API_KEY=tu-api-key-de-transcripcion

# ===================================================================
# 7. Rutas opcionales de CLI / Worker (si no estan en el PATH del sistema)
# ===================================================================
# YT_DLP_PATH=yt-dlp
# FFMPEG_PATH=ffmpeg
# YT_WORKER_POLL_DELAY=30000
```

---

## Ejecución en Entorno Local

Puedes ejecutar los dos componentes del sistema de forma independiente utilizando Maven Wrapper o mediante los artefactos `.jar` generados:

### Método A: Ejecución Directa con Maven Wrapper (Recomendado para desarrollo)

Abre dos terminales (una para `yt-api` y otra para `yt-worker`):

**Terminal 1 — Iniciar la API REST y Detector (`yt-api`):**
```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run -pl yt-api
```
```bash
# Linux / macOS
./mvnw spring-boot:run -pl yt-api
```
*`yt-api` se iniciará por defecto en el puerto `8083` (o el configurado en `application.yml`).*

**Terminal 2 — Iniciar el Trabajador de Descargas (`yt-worker`):**
```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run -pl yt-worker
```
```bash
# Linux / macOS
./mvnw spring-boot:run -pl yt-worker
```
*`yt-worker` se iniciará en el puerto `8081` y ejecutará periódicamente el sondeo de videos en estado `QUEUED_DOWNLOAD`.*

---

### Método B: Ejecución con Artefactos `.jar` Empaquetados

Si prefieres empaquetar y ejecutar los archivos compilados:

1. **Compilar el proyecto:**
   ```powershell
   .\mvnw.cmd package -DskipTests
   ```

2. **Cargar variables de `.env` y ejecutar `yt-api`:**
   ```powershell
   # Windows PowerShell
   Get-Content .env | ForEach-Object {
       if ($_ -match '^\s*([^#=]+)=(.*)$') {
           [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), "Process")
       }
   }
   java -jar yt-api/target/yt-api-0.0.1-SNAPSHOT.jar --server.port=8083
   ```
   ```bash
   # Linux / macOS
   export $(grep -v '^#' .env | xargs)
   java -jar yt-api/target/yt-api-0.0.1-SNAPSHOT.jar --server.port=8083
   ```

3. **Cargar variables de `.env` y ejecutar `yt-worker`:**
   ```powershell
   # Windows PowerShell
   Get-Content .env | ForEach-Object {
       if ($_ -match '^\s*([^#=]+)=(.*)$') {
           [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), "Process")
       }
   }
   java -jar yt-worker/target/yt-worker-0.0.1-SNAPSHOT.jar --server.port=8081
   ```
   ```bash
   # Linux / macOS
   export $(grep -v '^#' .env | xargs)
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

#### E. Sincronizar estaciones desde la Base de Datos de Reporting
Escanea la base de datos `Reporting` en la misma instancia de SQL Server (`id_country = 27` y `stream_owner LIKE '%youtube%'`), resuelve su handle/canal y los registra automáticamente en `MonitorTranscription`:
```bash
curl -X POST http://localhost:8083/api/v1/youtube/channels/sync-reporting \
  -H "X-API-Key: default-secret-key"
```
*(Nota: Este sincronizador también se ejecuta automáticamente cada 24 horas a las 04:00 AM UTC mediante un job programado `@Scheduled`).*

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
1. DETECCIÓN (yt-api)
   - WebSub recibe notificación push en tiempo real O el Polling consulta la playlist cada 30 min.
   - VideoDiscoveryService valida que el video no exista previamente en SQL Server.
   - Si la duración excede 10 horas, se marca SKIPPED.
   - Si es válido, se inserta en youtube.youtube_video con estado QUEUED_DOWNLOAD.

2. DESCARGA Y SEGMENTACIÓN (yt-worker)
   - DownloadJobListener sondea periódicamente (cada 30 s) videos con estado QUEUED_DOWNLOAD.
   - Al tomar un video, transiciona su estado a DOWNLOADING.
   - yt-dlp descarga exclusivamente el flujo de audio en un directorio temporal aislado.
   - ffmpeg recodifica a 16 kHz Mono 64kbps MP3 y lo divide en chunks de máximo 300 segundos.

3. SUBIDA A S3 Y DESPACHO AUTOMÁTICO (yt-worker)
   - Cada chunk se sube a S3 con convención: s3://monitorlatino/{Folder}/{YYYYMMDD}/{YYYYMMDD_HHMMSS}.mp3.
   - Se persiste cada fragmento en youtube.youtube_audio_chunk.
   - S3 notifica automáticamente a la cola de transcripción mediante eventos nativos de AWS (S3 Event Notification).
   - Se actualiza el video a TRANSCRIBING y se eliminan los archivos temporales de disco.

4. SINCRONIZACIÓN Y ENSAMBLADO FINAL (yt-api)
   - TranscriptionSyncService consulta periódicamente el estado de cada fragmento en Whisper.
   - Al completarse todos los fragmentos, el video pasa a COMPLETED.
   - VideoTranscriptAssemblyService suma los offsets temporales continuos de cada segmento para
     ofrecer la transcripción global sincronizada con la línea de tiempo de YouTube.
```

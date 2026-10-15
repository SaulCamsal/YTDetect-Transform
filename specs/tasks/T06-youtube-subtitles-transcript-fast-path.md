# Plan de Implementación SDD — T06: Extracción de Subtítulos y Transcripción Fast-Path

## 1. Contexto y Objetivos
Implementar una ruta rápida (**Fast-Path**) en `yt-worker` para extraer subtítulos nativos/automáticos de YouTube sin necesidad de descargar el archivo de audio ni invocar a Whisper.
Si un video cuenta con subtítulos en YouTube (español o idioma del canal):
1. Se extraen de forma ultrarrápida (< 1 segundo) con `yt-dlp` en formato estructurado (JSON3).
2. Se fragmentan en ventanas equivalentes a 5 minutos (300 segundos).
3. Para cada fragmento, se genera el texto continuo (`transcript_text`) y el arreglo de segmentos con timestamps comprimido en GZIP (`segments_gz`), idéntico al formato de Whisper.
4. Se persisten en la nueva tabla `youtube.youtubeSubtitleTranscript`.
5. El video se marca como completado sin consumir ancho de banda de audio, almacenamiento en S3 ni cómputo en Whisper.
6. Si el video **no** cuenta con subtítulos disponibles, el worker realiza un **fallback transparente** al flujo existente de descarga de audio, fragmentación con ffmpeg y subida a S3.

---

## 2. Definición de Tarea SDD: T06
- **Archivo de Tarea:** `specs/tasks/T06-youtube-subtitles-transcript-fast-path.md`
- **Contratos Involucrados:** `C1-database-schema.md` (ampliación de tabla 4 `youtube.youtubeSubtitleTranscript`).
- **Módulos Afectados:**
  - `yt-common`: Entidad JPA `YoutubeSubtitleTranscript` y repositorio `YoutubeSubtitleTranscriptRepository`.
  - `yt-worker`:
    - DTO `SubtitleCue` (`double start`, `double duration`, `String text`).
    - Servicio `SubtitleExtractionService`: orquesta la llamada a `yt-dlp` mediante `CliProcessRunner` y parsea los subtítulos JSON3.
    - Servicio `SubtitleChunkingService`: particiona los subtítulos en ventanas de 300 segundos y comprime `segments_gz` en GZIP.
    - Integración en `DownloadJobListener`: fast-path condicional antes de descargar audio, e inclusión de los fixes de persistencia de versión (`save` reasignado y reencolado en fallo).

---

## 3. Plan Paso a Paso

### Paso 1: Documentación de Esquema y Contrato
- Crear `db/create_table_youtube_subtitle_transcript.sql`.
- Actualizar `specs/contracts/C1-database-schema.md` con la tabla `youtube.youtubeSubtitleTranscript`.
- Crear la especificación formal de la tarea en `specs/tasks/T06-youtube-subtitles-transcript-fast-path.md`.

### Paso 2: Capa de Dominio en `yt-common`
- Crear entidad `com.monitorlatino.ytdetect.common.domain.entity.YoutubeSubtitleTranscript`.
- Crear repositorio `com.monitorlatino.ytdetect.common.domain.repository.YoutubeSubtitleTranscriptRepository`.

### Paso 3: Capa de Lógica en `yt-worker`
- Crear `com.monitorlatino.ytdetect.worker.dto.SubtitleCue`.
- Crear `com.monitorlatino.ytdetect.worker.service.SubtitleExtractionService`:
  - Ejecuta `yt-dlp --write-auto-sub --write-sub --sub-lang "es,es-419,es-ES,es-orig" --sub-format json3 --skip-download -o <workDir>/subs <youtubeUrl>`.
  - Parsea los eventos y segmentos (`tStartMs`, `dDurationMs`, `segs[].utf8`).
  - Retorna `Optional<List<SubtitleCue>>`. Si no hay subtítulos o el comando falla, retorna `Optional.empty()`.
- Crear `com.monitorlatino.ytdetect.worker.service.SubtitleChunkingService`:
  - Agrupa cues en buckets de 300s (`0-300`, `300-600`, etc.).
  - Construye `transcript_text`.
  - Construye el JSON de segmentos con `start` y `end` relativos y absolutos.
  - Comprime con `java.util.zip.GZIPOutputStream` a `byte[]` para `segments_gz`.
  - Calcula `segments_raw_bytes` y `segment_count`.
- Integrar en `DownloadJobListener`:
  - Aplicar `save()` con reasignación de versión: `video = videoRepository.save(video);`.
  - Antes de descargar audio: si `yt.worker.fast-path.enabled` es `true` (por defecto `true`), intentar extracción de subtítulos.
  - Si existen subtítulos: generar y guardar los registros en `youtubeSubtitleTranscriptRepository`, actualizar video a `COMPLETED` (o `AUDIO_READY` con `chunkCount`), y salir.
  - Si no existen subtítulos: continuar con el flujo normal de descarga de audio, ffmpeg y subida a S3.
  - Mejorar `handleFailure`: recargar video de la BD y si `attemptCount < maxAttempts`, reencolar a `QUEUED_DOWNLOAD`.

### Paso 4: Pruebas Unitarias
- `SubtitleExtractionServiceTest`: simula salida de `yt-dlp` exitosa y sin subtítulos.
- `SubtitleChunkingServiceTest`: valida el particionado en bloques de 300s, texto concatenado y descompresión correcta de `segments_gz`.
- `DownloadJobListenerTest`: valida el camino rápido cuando hay subtítulos y el camino tradicional cuando no los hay.
- Ejecutar `.\mvnw.cmd test` verificando que todos los módulos pasen al 100%.

### Paso 5: Handoff
- Crear `specs/handoff/T06.md`.

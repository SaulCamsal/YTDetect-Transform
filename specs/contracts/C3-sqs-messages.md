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

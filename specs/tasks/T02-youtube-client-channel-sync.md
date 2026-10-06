# T02 - Cliente de YouTube Data API v3 y Sincronización de Canales

**Módulo**: `yt-api`
**Rama**: `feat/T01-common-module-setup` (o `feat/T02-channel-sync`)

## Objetivo
Configurar el módulo ejecutable `yt-api`, implementar el cliente autenticado de YouTube Data API v3, el servicio de sincronización de suscripciones (`subscriptions.list?mine=true`), la administración de canales (`YoutubeChannelService`) y sus endpoints REST correspondientes conforme al contrato C2.

## Archivos que te pertenecen
- `pom.xml` (raíz - agregar módulo `yt-api`)
- `yt-api/pom.xml`
- `yt-api/src/main/resources/application.yml`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/YtApiApplication.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/config/YoutubeConfig.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/config/SchedulerConfig.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/client/YoutubeApiClient.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/dto/*.java` (records de petición/respuesta de canales)
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubeSubscriptionSyncService.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/service/YoutubeChannelService.java`
- `yt-api/src/main/java/com/monitorlatino/ytdetect/api/controller/ChannelController.java`
- Tests en `yt-api/src/test/...`

## Requerimientos

1. **Módulo `yt-api`**:
   - Depende de `yt-common`, `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `mssql-jdbc`.
   - Incluye cliente de Google: `google-api-services-youtube` y `google-auth-library-oauth2-http`.
   - Soporte para ShedLock (`shedlock-spring` y `shedlock-provider-jdbc-template`).
2. **Cliente YouTube**:
   - Soporte para OAuth2 (`youtube.readonly`) usando client id, client secret y refresh token, o API Key como fallback cuando no hay credenciales de usuario configuradas.
   - Paginación transparente para `subscriptions.list?mine=true`.
   - Búsqueda en lote de detalles de canal (`channels.list?id=...&part=contentDetails,snippet`) para obtener `uploadsPlaylistId` (de `contentDetails.relatedPlaylists.uploads`).
3. **Servicio de Sincronización**:
   - Upsert en `youtube_channel`: Si el canal ya existe con `origin = SUBSCRIPTION`, actualiza nombre y `uploads_playlist_id`. Si es nuevo, lo crea con `origin = SUBSCRIPTION`, `enabled = false` (o valor default).
   - Ejecución programada con `@Scheduled(cron = "${yt.sync.cron:0 0 3 * * *}")` y candado ShedLock `@SchedulerLock(name = "youtube_subscription_sync", lockAtMostFor = "15m")`.
4. **Controlador REST**:
   - Implementa los endpoints definidos en `specs/contracts/C2-api-endpoints.md`:
     - `POST /api/v1/youtube/channels`
     - `GET /api/v1/youtube/channels`
     - `PATCH /api/v1/youtube/channels/{id}`
     - `POST /api/v1/youtube/channels/sync`
   - Validación de entradas (`@Valid`, `@NotBlank`).
   - Todos los DTOs como `record`.
5. **Pruebas**:
   - Pruebas unitarias de `YoutubeSubscriptionSyncService` simulando llamadas al cliente de YouTube.
   - Pruebas de integración o slice MVC para `ChannelController`.

## Lee antes de empezar
- `specs/contracts/C1-database-schema.md`
- `specs/contracts/C2-api-endpoints.md`
- `specs/contracts/C5-external-apis.md`
- `specs/00-constitution.md`

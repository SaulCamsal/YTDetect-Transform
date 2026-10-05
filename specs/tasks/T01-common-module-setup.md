# T01 - Configuración Inicial del Módulo yt-common

**Módulo**: `yt-common`
**Rama**: `feat/T01-common-module-setup`

## Objetivo
Preparar el módulo base (`yt-common`) que los módulos `yt-api` y `yt-worker` importarán. Crear las entidades de dominio JPA y los repositorios correspondientes, de forma estricta según el contrato C1.

## Archivos que te pertenecen
(Puedes crear todos estos).
- `pom.xml` (raíz - agregar dependencias globales, Spring Boot, etc si es necesario iniciar el multi módulo). O si ya está, crear `yt-common/pom.xml`.
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/entity/YoutubeChannel.java`
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/entity/YoutubeVideo.java`
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/entity/YoutubeAudioChunk.java`
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/enums/*.java`
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/repository/YoutubeChannelRepository.java`
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/repository/YoutubeVideoRepository.java`
- `yt-common/src/main/java/com/monitorlatino/ytdetect/common/domain/repository/YoutubeAudioChunkRepository.java`

## Requerimientos

1. **Estructura Maven**: Asegura de que el `pom.xml` de `yt-common` incluya `spring-boot-starter-data-jpa`, `mssql-jdbc` y que **NO** se incluya Lombok. Usa Java 21 en maven-compiler-plugin.
2. **Entidades JPA**: Mapea fielmente a las tablas definidas en `specs/contracts/C1-database-schema.md`.
   - Recuerda usar `IDENTITY` (`GenerationType.IDENTITY`).
   - Usa esquema `youtube` en la anotación `@Table(schema = "youtube", name = "youtube_...")`.
   - Propiedades de tiempo: Se sugiere `Instant` para fechas UTC (`created_at`, `updated_at`, `published_at`) y `LocalDateTime` para `aired_at_local` o `timeline_cursor` que representan hora local. Mantenlo consistente.
   - Para la tabla `shedlock`, la librería Shedlock la maneja directamente, no es necesario hacer una entidad JPA. Documenta la decisión en tu handoff.
3. **Optimistic Locking**: Las tablas `youtube_channel` y `youtube_video` tienen un campo `version`. Usa `@Version`.
4. **Repositorios**: Spring Data JPA Interfaces.
5. **No hay Logica de Negocio**: Solo modelo, persistencia y configuración básica de base de datos.

## Pruebas
1. Agrega un test simple de persistencia usando `@DataJpaTest` en `yt-common/src/test/java/...` o un test de repositorio usando Testcontainers con el archivo `db/V1__youtube_schema.sql` (opcional, pero sugerido).
   - Recuerda: `spring.jpa.hibernate.ddl-auto=none` (o validate) y usa Flyway/Testcontainers para levantar el esquema.

## Lee antes de empezar
- `specs/contracts/C1-database-schema.md`
- `specs/00-constitution.md`

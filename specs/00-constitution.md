# 00 - Constitución del Proyecto

Esta es la carta magna técnica de **YTDetect-Transform**. Todo agente debe respetar estas reglas globales, que actúan como base para las decisiones de diseño y el código generado.

## 1. Naturaleza del Proyecto

YTDetect-Transform es la **capa de ingesta de YouTube** que detecta nuevos videos (hasta 10 horas de duración) de los canales suscritos, descarga el audio usando `yt-dlp` y `ffmpeg` para fragmentarlo en trozos de 5 minutos, los sube a S3, y solicita su transcripción a una **API de Transcripción externa y ya existente**.
**NO construimos un sistema de transcripción**, somos los clientes.

## 2. Pila Tecnológica
- **Java 21**. Sin excepciones.
- **Spring Boot 4.x** (Última estable). Usa `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, etc.
- **Spring Cloud AWS 4.2.0** (compatible con Boot 4.x) para SQS y S3.
- **SQL Server** (AWS RDS). JDBC de Microsoft (`mssql-jdbc`).
- **Maven Wrapper**. Todos los comandos corren con `./mvnw` (o `.\mvnw.cmd`).

## 3. Patrones de Diseño Obligatorios

### Módulos
El proyecto usa Maven multi-módulo:
- `yt-common`: Entidades JPA, Repositorios, clientes HTTP compartidos, y DTOs de dominio. (No ejecutable).
- `yt-api`: Expone la API REST, se conecta a YouTube API, contiene los Schedulers, Sincronización WebSub. Depende de `yt-common`. Desplegado en ALB.
- `yt-worker`: Aplicación SQS listener (`@SqsListener`), ejecuta descarga de video (yt-dlp, ffmpeg) y sube a S3, manda peticiones a API externa. Depende de `yt-common`. Desplegado en ECS sin GPU.

### Código
- **Inyección por Constructor**: `@Autowired` en campos está prohibido. Usa `final` para dependencias en el constructor.
- **Lombok**: **PROHIBIDO**. Generar getters, setters, constructores y `equals/hashCode` con el IDE.
- **DTOs**: Usa records de Java 21 (`public record VideoDto(String id, ...) {}`) para DTOs, request, responses y eventos.
- **Manejo de Tiempos**: Todo debe usar `java.time.Clock`. Configurar un `@Bean Clock clock() { return Clock.systemUTC(); }`. Nunca uses `LocalDateTime.now()` sin pasar el `Clock`.
- **Logs**: Usa SLF4J (`org.slf4j.Logger`). Los mensajes deben estar en inglés y usar placeholders: `log.info("Processing chunk {} for video {}", chunkIndex, videoId);`. No loggear secretos.

### Base de datos
- Esquema manual (Flyway NO corre en runtime para evitar permisos DDL en producción). Usa la BD y schema dictado en `C1-database-schema.md`.
- Hibernate: `spring.jpa.hibernate.ddl-auto=validate`.
- Concurrencia: `ShedLock` para que solo 1 instancia de `yt-api` ejecute un `@Scheduled` a la vez.

## 4. Colaboración de Agentes
Como los agentes corren en paralelo:
- **Respeta tu scope**: Toca SOLO los archivos que tu tarea indique explícitamente.
- **Mockea ausencias**: Si llamas a un servicio que TXX debió crear pero no está, asume la firma según el contrato o crea una interfaz `Dummy` y anótalo en tu Handoff. No bloquees tu compilación si puedes simular la dependencia.
- **Contratos son sagrados**: `specs/contracts/` no debe modificarse. Si algo está mal o es ambiguo, se documenta en el Handoff.

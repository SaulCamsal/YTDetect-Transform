# AGENTS.md — Reglas obligatorias para todos los agentes

Proyecto **YTDetect-Transform**: servicio Java/Spring Boot que detecta videos nuevos de canales de YouTube, descarga solo el audio, lo divide en fragmentos de 5 min, lo sube a S3 y lo manda a la **API de transcripción existente** (Whisper). No construimos un sistema de transcripción; solo la capa de ingesta.

Este repositorio se desarrolla con **Spec Driven Development**. Las especificaciones están en `specs/` y **son la fuente de verdad**.

## 1. Antes de escribir código

1. Lee, en este orden:
   1. `specs/README.md`
   2. `specs/00-constitution.md`
   3. Tu tarea: `specs/tasks/Txx-*.md`
   4. Solo los contratos que tu tarea lista en "Lee antes de empezar" (`specs/contracts/C*.md`).
2. No necesitas `Context Docs/`. Todo lo necesario ya está en los contratos.
3. Si algo de la spec es ambiguo, elige la interpretación **más simple que cumpla los criterios de aceptación** y anótala en tu handoff como "Decisión".

## 2. Reglas de propiedad de archivos (CRÍTICO: trabajamos en paralelo)

- Solo puedes **crear o modificar** los archivos y paquetes listados en tu tarea bajo "Archivos que te pertenecen".
- **Nunca** modifiques sin que tu tarea lo diga explícitamente:
  - `pom.xml` (cualquiera), `mvnw*`, `.mvn/`
  - `application*.yml`, `yt-defaults.yml`
  - `db/` (es del humano)
  - `specs/contracts/` (son contratos congelados)
  - Clases creadas por T01 (entidades, repositorios, puertos/interfaces, DTOs de contrato, `YtProperties`)
- Si necesitas algo que no existe (una dependencia, un método de repositorio, un campo de configuración, cambiar una firma):
  1. **No lo agregues.**
  2. Busca una alternativa dentro de tus archivos (p. ej. una consulta con `JpaSpecificationExecutor`, o lógica en tu servicio).
  3. Si no hay alternativa, escribe `BLOQUEO` o `CONTRACT ISSUE` en tu handoff, explica el cambio exacto que necesitas y termina el resto de la tarea.

## 3. Reglas de código

- Java 21. Spring Boot 4.x (ver versiones en la constitución). **No cambies versiones ni agregues dependencias.**
- Inyección por constructor. **Sin Lombok.** Sin `@Autowired` en campos.
- DTOs y mensajes = `record`.
- Tiempo: siempre con el bean `java.time.Clock` (UTC). Nunca `LocalDateTime.now()` sin `Clock`.
- Logs con SLF4J y placeholders (`log.info("Video {} queued", videoId)`). Nunca `System.out`. Nunca registres secretos (API keys, tokens, contraseñas).
- Identificadores, comentarios y mensajes de log en **inglés**. Las specs están en español.
- No inventes campos de APIs externas. Usa exactamente lo que dice `specs/contracts/C5-external-apis.md`.
- No uses APIs de Spring Boot 3 que cambiaron en Boot 4 (ver sección "Spring Boot 4" de la constitución).

## 4. Pruebas y build

- Toda clase con lógica lleva pruebas. Los nombres de prueba exactos que pide tu tarea son obligatorios.
- Unit tests: `*Test.java`. Integración (Docker / Testcontainers): `*IT.java`. Ambos corren con `verify`.
- Comando (Windows PowerShell): `.\mvnw.cmd -B -pl <modulo> -am verify`
- Comando (Linux/macOS/CI): `./mvnw -B -pl <modulo> -am verify`
- **No termines la tarea con el build en rojo.** Si un test de integración no corre porque falta Docker o ffmpeg en la máquina, dilo en el handoff con la salida del error.

## 5. Git

- Trabaja en tu rama: `feat/Txx-<slug>` (la indica tu tarea). Si usas worktree, ya estás en ella.
- Commits pequeños: `Txx: <qué hiciste>`.
- **No hagas merge a `main`.** Lo hace el humano después de revisar.

## 6. Al terminar

Crea `specs/handoff/Txx.md` con la plantilla de `specs/README.md` (sección "Plantilla de handoff"). Sin handoff la tarea no se considera entregada.

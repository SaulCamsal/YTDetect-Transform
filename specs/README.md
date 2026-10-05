# Spec Driven Development (SDD) para YTDetect-Transform

Este directorio contiene todas las especificaciones y tareas para construir la capa de ingesta de YouTube, operada por agentes de IA trabajando en paralelo.

## ¿Por qué SDD?

Dado que múltiples agentes de IA (como Gemini Flash 3.8) trabajarán en paralelo en diferentes ramas y módulos (`yt-api`, `yt-worker`), no pueden depender del estado actual de la rama `main` ni esperar a que un agente termine su parte para empezar la de ellos.

El SDD permite el trabajo en paralelo mediante **Contratos Duros**. 
Un agente que construye la API que inserta en la base de datos se guía por el contrato de la base de datos (`C1-database-schema.md`) y no por la existencia física de la entidad de JPA (que puede estar siendo desarrollada por otro agente).

## Estructura

- `00-constitution.md`: Reglas base del proyecto (arquitectura, versiones, convenciones de Spring Boot, manejo de concurrencia). **Lectura obligatoria para entender el contexto global**.
- `contracts/`: Archivos `C<número>-<tema>.md`. Son la **fuente de verdad técnica**. Si dice que el endpoint de transcripción usa `X-API-Key`, usa `X-API-Key`.
  - `C1-database-schema.md`: Modelo de datos (schema `youtube`), tipos y nombres exactos.
  - `C5-external-apis.md`: Definición de la API de Transcripción y cómo interactuamos con YouTube Data API.
- `tasks/`: Archivos `T<número>-<slug>.md`. Define exactamente **qué tiene que hacer un agente en una invocación**.
- `handoff/`: Directorio donde el agente guarda el resultado de su tarea.

## Flujo de un Agente

1. El usuario asigna al agente un archivo de la carpeta `tasks/` (ej. `T01-common-module-setup.md`).
2. El agente lee obligatoriamente `README.md` (este archivo), `00-constitution.md`, su tarea, y los contratos que indique la tarea.
3. El agente lee `AGENTS.md` en la raíz del repositorio.
4. El agente ejecuta la tarea paso a paso. Se limita estrictamente a los "Archivos que te pertenecen" definidos en la tarea.
5. Si un test o compilación falla porque falta una dependencia de otra tarea en paralelo (ej. un servicio que aún no existe), el agente debe simularlo (mock) o documentar el bloqueo en el handoff. ¡Nunca modificar archivos fuera de los asignados!
6. Al finalizar, el agente crea `specs/handoff/Txx.md` copiando la plantilla de abajo, y finaliza su ejecución.

## Plantilla de handoff

```md
# Handoff Tarea TXX

**Agente:** (tu identificador)
**Estado:** (COMPLETED | BLOCKED)

## Resumen de cambios
- Se crearon las entidades X, Y, Z.
- Se configuró el pom.xml.

## Decisiones tomadas
- Se usó tipo X para la columna Y porque la spec no era clara sobre el formato exacto.

## Bloqueos / Contract Issues
(Dejar vacío si no hubo. Si algo en la spec es imposible o contradictorio, explícalo aquí detalladamente).
```

# C4 - Protocolo WebSub y Notificaciones Atom

Este contrato define la interacción de WebSub (PubSubHubbub) con el Hub de Google (`https://pubsubhubbub.appspot.com/`) y los feeds Atom de YouTube.

## 1. Hub y Tópicos
- URL del Hub: `https://pubsubhubbub.appspot.com/`
- Formato del Topic URL:
  `https://www.youtube.com/feeds/videos.xml?channel_id={CHANNEL_ID}`
- Callback URL pública:
  `https://{DOMAIN}/api/v1/youtube/websub/callback`

## 2. Suscripción y Verificación (GET Callback)
Cuando `WebSubService` solicita la suscripción al Hub (`hub.mode=subscribe`), el Hub realiza una petición `GET` a nuestro endpoint de callback para verificar la intención.

**Parámetros de consulta (Query Params):**
- `hub.mode`: `subscribe` | `unsubscribe` | `denied`
- `hub.topic`: URL del feed del canal
- `hub.challenge`: Cadena aleatoria generada por el hub
- `hub.lease_seconds`: Duración del arrendamiento en segundos (ej. 432000 = 5 días)

**Respuesta requerida:**
- Código HTTP: `200 OK`
- Content-Type: `text/plain`
- Cuerpo: Exactamente el valor de `hub.challenge`.

## 3. Notificación de Nuevos Videos (POST Callback)
Cuando un canal publica o actualiza un video, el Hub envía un `POST` con un feed Atom XML.

**Cabecera de Seguridad:**
- `X-Hub-Signature`: `sha1={HMAC}` o `sha256={HMAC}`
- Se calcula sobre el cuerpo crudo (raw bytes) de la petición usando el `hub.secret` configurado. Si no coincide, responder `403 Forbidden`.

**Estructura del Payload (Atom XML):**
```xml
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
      xmlns="http://www.w3.org/2005/Atom">
  <link rel="is" href="http://www.youtube.com/channel/UC..."/>
  <title>Canal Ejemplo</title>
  <updated>2026-10-05T12:00:00+00:00</updated>
  <entry>
    <id>yt:video:abc123xyz</id>
    <yt:videoId>abc123xyz</yt:videoId>
    <yt:channelId>UC123456</yt:channelId>
    <title>Título del video</title>
    <link rel="alternate" href="https://www.youtube.com/watch?v=abc123xyz"/>
    <author>
      <name>Canal Ejemplo</name>
      <uri>https://www.youtube.com/channel/UC123456</uri>
    </author>
    <published>2026-10-05T11:59:00+00:00</published>
    <updated>2026-10-05T12:00:00+00:00</updated>
  </entry>
</feed>
```

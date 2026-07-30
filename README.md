# Chatbot web con Elastic Agent Builder

Aplicación web que envía un *prompt* a un agente de **Elastic Agent Builder**
(Elastic 9.4.3) y muestra la respuesta **en vivo** (streaming SSE) en el
navegador. Cada interacción es independiente: **no hay memoria conversacional**.

La app actúa como **proxy de streaming** entre el navegador y Kibana. La API Key
vive solo en el backend y **nunca** llega al navegador.

> **Nota clave:** el endpoint para conversar con un agente vive en **Kibana**
> (`/api/agent_builder/converse/async`), no en el nodo de Elasticsearch (`:9200`).
> Por eso `KIBANA_BASE_URL` debe apuntar a Kibana.

---

## Stack

- **Java 25 (LTS)** · **Spring Boot 4.1.0** · **Maven**
- **Thymeleaf** para la interfaz + JavaScript (`fetch` + `ReadableStream`) para leer el stream
- **Spring WebFlux `WebClient`** para consumir el SSE de Kibana (la app sigue corriendo sobre Tomcat/servlet)
- **Jackson 3** (`tools.jackson.*`) para JSON — lo trae Spring Boot 4 (paquete nuevo; anotaciones aún en `com.fasterxml.jackson.annotation`)
- **marked** + **DOMPurify** (vendorizados) para renderizar Markdown de forma segura en el navegador
- Virtual threads de Java 25 activados (`spring.threads.virtual.enabled=true`)

---

## Requisitos previos

**Del lado de Elastic (los provee tu administrador):**

- Cluster de Elastic 9.4.3 con **Agent Builder habilitado** (GA desde 9.4).
- Un agente creado en Agent Builder; necesitas su **`agent_id`** (por defecto `demo-seguros-siem`).
- **URL de Kibana** accesible desde donde corre la app.
- **API Key** de Kibana con permisos de Agent Builder.
- Nombre del **Space** si el agente no está en el Space por defecto.

**Del lado de desarrollo:**

- JDK 25 y Maven.

---

## Configuración

La configuración vive en `src/main/resources/application.yml`. Todos los valores
tienen ya un **valor por defecto**, así que la app arranca sin definir ninguna
variable. Cada variable de entorno, si se define, **tiene prioridad** sobre el
default:

| Variable            | Descripción                                                      |
|---------------------|-----------------------------------------------------------------|
| `KIBANA_BASE_URL`   | URL base de **Kibana**. Por defecto apunta al cluster del proyecto. |
| `ELASTIC_API_KEY`   | API Key de Kibana con permisos de Agent Builder                 |
| `ELASTIC_AGENT_ID`  | `agent_id` (por defecto `demo-seguros-siem`)                    |
| `ELASTIC_SPACE`     | Space de Kibana (vacío = space por defecto)                     |

Defaults actuales en `application.yml`:

- `kibana-base-url`: `https://bigfito-serverless-security-ebcf18.kb.us-central1.gcp.elastic.cloud`
- `agent-id`: `demo-seguros-siem`
- `api-key`: **incluida en el archivo** (ver advertencia de seguridad más abajo).

> ⚠️ **Advertencia de seguridad:** la API Key está escrita en `application.yml`, que es
> un archivo versionado. **No subas este archivo a un repositorio público** y **rota la
> clave** en Kibana si se expone. Para un despliegue real, elimina la clave del archivo
> y pásala solo por la variable de entorno `ELASTIC_API_KEY`:
>
> ```bash
> export ELASTIC_API_KEY="<api_key_de_kibana>"
> ```

---

## Cómo ejecutar

```bash
# 1) Exporta las variables de entorno (ver arriba)
# 2) Arranca la aplicación
mvn spring-boot:run
```

Abre <http://localhost:8080>, escribe una pregunta y pulsa **Enviar**
(o `Ctrl`/`Cmd` + `Enter`).

### Empaquetar

```bash
mvn clean package
java -jar target/agent-chat-0.0.1.jar
```

---

## Interfaz de usuario

La página (`/`) es un chat de una sola pantalla con dos zonas de distinto tono
(esquema de color **pastel**, tema claro):

- **Área de conversación** (azul pastel): muestra el historial como burbujas
  claramente diferenciadas por rol:
  - **Tú** — el prompt del usuario, alineado a la derecha (lila pastel). Se pinta
    como texto plano (nunca HTML).
  - **Agente** — la respuesta, alineada a la izquierda (blanco), renderizada en
    **Markdown** en vivo mientras llega el stream, con un cursor parpadeante.
- **Caja de entrada** (rosa pastel, tono distinto al de la respuesta): el
  `textarea` y los botones.

Comportamiento:

- **Enviar**: manda la pregunta. Atajo: `Ctrl`/`Cmd` + `Enter`.
- Tras una respuesta **correcta**, el `textarea` **se limpia automáticamente**. Si
  hubo un error, el texto **se conserva** para reintentar.
- **Reiniciar** (a la izquierda de *Enviar*): reinicia la app **desde cero en el
  cliente** — cancela cualquier respuesta en curso (aborta la petición), borra la
  conversación y el prompt, y deja la UI como recién abierta. Pide **confirmación**
  si hay algo que se vaya a perder. No recarga la página ni afecta al servidor
  (el backend es sin estado).

> **Historial solo visual:** la app **no** tiene memoria conversacional. El
> historial que se ve en pantalla no se reenvía al agente; cada pregunta se procesa
> de forma independiente.

---

## Probar la conectividad con curl (opcional)

```bash
curl -X POST "https://bigfito-serverless-security-ebcf18.kb.us-central1.gcp.elastic.cloud/api/agent_builder/converse" \
     -H "Authorization: ApiKey ${ELASTIC_API_KEY}" \
     -H "kbn-xsrf: true" \
     -H "Content-Type: application/json" \
     -d '{ "input": "¿Qué es Elasticsearch?", "agent_id": "demo-seguros-siem" }'
```

Para *streaming*, usa el endpoint `/api/agent_builder/converse/async` (con el
prefijo `/s/<space>` si usas un Space distinto al de por defecto).

---

## Estructura del proyecto

```
src/main/java/com/bigfito/agentchat/
├── AgentChatApplication.java
├── config/         ElasticAgentProperties · WebClientConfig
├── web/            ChatViewController (GET /) · ChatStreamController (POST /api/chat/stream, SSE)
├── service/        ChatService (validación + orquestación)
├── client/         AgentBuilderClient (interfaz) · AgentBuilderWebClient (WebClient)
├── model/          PromptRequest · ConverseRequest · StreamChunk
└── exception/      ErrorCode · jerarquía de excepciones · GlobalExceptionHandler

src/main/resources/
├── application.yml
├── templates/index.html            # UI del chat (Thymeleaf)
└── static/
    ├── css/chat.css                # esquema pastel + estilos de burbujas y Markdown
    └── js/
        ├── chat.js                 # streaming SSE, vista de conversación, reinicio
        └── vendor/                 # librerías vendorizadas (sin CDNs externos)
            ├── marked.min.js       # parser de Markdown
            └── purify.min.js       # DOMPurify (saneado anti-XSS)
```

> `.claude/launch.json` es una configuración auxiliar para arrancar la app desde el
> panel de vista previa de Claude Code; no es necesaria para ejecutar el proyecto.

---

## Manejo de errores

Los fallos se traducen a códigos significativos (`ErrorCode`):

| Código            | Situación                          | Acción sugerida                     |
|-------------------|------------------------------------|-------------------------------------|
| `PROMPT_EMPTY`    | Prompt vacío                       | Escribir una pregunta               |
| `PROMPT_TOO_LONG` | Prompt supera 4000 caracteres      | Acortar el texto                    |
| `AUTH_INVALID`    | API Key inválida/expirada (401)    | Regenerar la API Key en Kibana      |
| `FORBIDDEN`       | Sin permisos de Agent Builder (403)| Ajustar privilegios del rol         |
| `AGENT_NOT_FOUND` | `agent_id` inexistente (404)       | Verificar el id del agente          |
| `UPSTREAM_ERROR`  | Fallo en Kibana/Elastic (5xx)      | Reintentar / avisar a operaciones   |
| `TIMEOUT`         | El agente tardó demasiado          | Reintentar; revisar `response-timeout` |

- Si el fallo ocurre **antes** de abrir el stream → respuesta HTTP con código y mensaje.
- Si ocurre **durante** el stream → evento SSE `error` con `{code, message}`.

---

## Pruebas

```bash
mvn test
```

Suite de **11 pruebas** (backend):

- `ChatServiceTest`: validación del prompt (vacío / demasiado largo).
- `AgentBuilderWebClientTest`: simula el stream SSE de Kibana con **MockWebServer**
  (formato real: tipo en la línea `event:`, texto en `data.text_chunk`) y verifica
  los `StreamChunk`, además del mapeo de 401/403/5xx a excepciones propias.
- `ChatStreamControllerTest`: verifica la secuencia de eventos SSE (`message…` + `done`)
  y la conversión de un fallo en un evento `error`.

---

## Notas y decisiones de diseño

- **Sin memoria conversacional:** no se envía `conversation_id`. El historial de la
  pantalla es **solo visual** (no se reenvía al agente). Para habilitar memoria
  multi-turno, captura el `conversation_id` que devuelve el stream y añádelo a
  `ConverseRequest`.
- **Vista de conversación:** cada turno añade una burbuja del usuario y otra del
  agente, diferenciadas por alineación, color y etiqueta de rol. El área acumula el
  historial de la sesión en el navegador.
- **Reinicio en el cliente:** el botón *Reiniciar* aborta la petición en curso
  (`AbortController`), limpia la conversación y el estado y restaura los controles,
  sin recargar la página. Como el backend es **sin estado**, no hay nada que
  reiniciar en el servidor; si en el futuro se añade estado (p. ej. memoria), habría
  que ampliar el reinicio para notificarlo.
- **Limpieza del prompt:** tras una respuesta correcta el `textarea` se vacía; ante
  un error se conserva el texto para reintentar.
- **Parser SSE tolerante:** solo se propagan los eventos con texto visible
  (`message_chunk`); el resto se ignora. Confirmado contra Kibana 9.4.x: el tipo de
  evento viaja en la **línea `event:` del SSE** (no en un campo `type` del JSON) y el
  texto está en `data.text_chunk`. El parser no se rompe ante tipos desconocidos.
- **Render de Markdown (seguro):** la respuesta del agente llega en Markdown y se
  renderiza en el navegador con [`marked`](https://marked.js.org) + saneado con
  [`DOMPurify`](https://github.com/cure53/DOMPurify) antes de inyectarla en el DOM
  (nunca `innerHTML` sin sanear → evita XSS). Las librerías están **vendorizadas** en
  `static/js/vendor/` (sin CDNs externos). Si no cargaran, la UI cae a texto plano.
- **Seguridad de la propia app:** esta versión asume una app **interna sin login**.
  Si se expone públicamente, conviene añadir `spring-boot-starter-security`
  (autenticación de usuarios + protección CSRF, exponiendo el token CSRF en la página
  y enviándolo como cabecera desde `chat.js`) y un *rate limiting* por sesión/IP.
- **Proxies inversos (Nginx, etc.):** desactiva el *buffering* de respuestas para que
  el SSE fluya en vivo (`proxy_buffering off;`).

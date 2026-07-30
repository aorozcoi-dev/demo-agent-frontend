package com.bigfito.agentchat.client;

import com.bigfito.agentchat.config.ElasticAgentProperties;
import com.bigfito.agentchat.exception.AgentAuthenticationException;
import com.bigfito.agentchat.exception.AgentUpstreamException;
import com.bigfito.agentchat.exception.ErrorCode;
import com.bigfito.agentchat.model.ConverseRequest;
import com.bigfito.agentchat.model.StreamChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Implementación del {@link AgentBuilderClient} usando {@link WebClient}.
 *
 * <p>Hace una llamada HTTP directa al endpoint de streaming de Kibana
 * ({@code /api/agent_builder/converse/async}) con las cabeceras
 * {@code Authorization: ApiKey ...} y {@code kbn-xsrf: true}, y transforma los
 * eventos SSE recibidos en {@link StreamChunk} con el texto a mostrar.</p>
 *
 * <p>El parser de eventos es <strong>tolerante</strong>: solo propaga los
 * eventos que aportan texto visible y <em>ignora</em> los desconocidos, de modo
 * que no se rompe si Elastic añade nuevos tipos de evento.</p>
 */
@Component
public class AgentBuilderWebClient implements AgentBuilderClient {

    private static final Logger log = LoggerFactory.getLogger(AgentBuilderWebClient.class);

    /** Cabecera anti-CSRF obligatoria en las APIs de Kibana. */
    private static final String KBN_XSRF_HEADER = "kbn-xsrf";

    /**
     * Nombre del evento SSE que transporta un fragmento de texto del mensaje.
     * <p>Confirmado contra el endpoint real de Kibana 9.4.x: el endpoint emite
     * varios eventos ({@code conversation_id_set}, {@code reasoning},
     * {@code message_chunk}, {@code message_complete}, {@code round_complete},
     * ...) y el tipo viaja en la <em>línea {@code event:}</em> del SSE, no dentro
     * del JSON. Aquí solo nos interesan los {@code message_chunk}.</p>
     */
    private static final String EVENT_MESSAGE_CHUNK = "message_chunk";

    private final WebClient webClient;
    private final ElasticAgentProperties props;
    private final ObjectMapper objectMapper;

    public AgentBuilderWebClient(WebClient kibanaWebClient,
                                 ElasticAgentProperties props,
                                 ObjectMapper objectMapper) {
        this.webClient = kibanaWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<StreamChunk> converseStream(String prompt) {
        var body = new ConverseRequest(prompt, props.agentId()); // sin conversation_id (sin memoria)

        return webClient.post()
                .uri(props.converseStreamPath())
                .header(HttpHeaders.AUTHORIZATION, "ApiKey " + props.apiKey())
                .header(KBN_XSRF_HEADER, "true")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapUpstreamError)
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() { })
                .mapNotNull(this::toStreamChunk)
                .onErrorMap(this::isTimeout, this::toTimeoutException)
                .doOnSubscribe(s -> log.info("Iniciando streaming con el agente '{}'", props.agentId()))
                .doOnComplete(() -> log.info("Streaming finalizado con el agente '{}'", props.agentId()))
                .doOnError(e -> log.error("Error durante el streaming con Agent Builder", e));
    }

    /**
     * Convierte un evento SSE de Kibana en un {@link StreamChunk} con el texto a
     * mostrar. Se discrimina por el <strong>nombre del evento</strong> (la línea
     * {@code event:} del SSE): solo se propagan los {@code message_chunk}; el
     * resto (razonamiento, id de conversación, fin de turno, ...) se ignora
     * devolviendo {@code null}.
     *
     * @param event evento SSE recibido de Kibana.
     * @return fragmento de texto, o {@code null} si el evento no aporta texto.
     */
    private StreamChunk toStreamChunk(ServerSentEvent<String> event) {
        String data = event.data();
        if (data == null || data.isBlank()) {
            return null; // comentarios/keep-alive del stream (líneas ":") u otros sin datos
        }
        if (!isMessageChunk(event.event(), data)) {
            log.debug("Evento SSE ignorado (event='{}')", event.event());
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            String delta = extractDeltaText(root);
            return (delta == null || delta.isEmpty()) ? null : new StreamChunk(delta);
        } catch (Exception e) {
            // No rompemos el stream por un evento suelto no parseable: se registra y se ignora.
            log.warn("Evento SSE no parseable, se ignora [{}]", ErrorCode.STREAM_PARSE, e);
            return null;
        }
    }

    /**
     * ¿El evento es un fragmento de texto? El tipo viaja en la línea {@code event:}
     * del SSE. Como respaldo (tolerancia a variaciones), si no viniera ahí se busca
     * un campo {@code type} dentro del JSON.
     */
    private boolean isMessageChunk(String eventName, String data) {
        if (EVENT_MESSAGE_CHUNK.equals(eventName)) {
            return true;
        }
        if (eventName == null) {
            try {
                return EVENT_MESSAGE_CHUNK.equals(objectMapper.readTree(data).path("type").asText(""));
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Extrae el texto del delta de un evento {@code message_chunk}.
     * Se prueban varias rutas conocidas para ser tolerante a variaciones del
     * esquema entre versiones de Elastic.
     */
    private String extractDeltaText(JsonNode root) {
        JsonNode dataNode = root.path("data");
        if (dataNode.hasNonNull("text_chunk")) {
            return dataNode.get("text_chunk").asText();
        }
        if (dataNode.hasNonNull("content")) {
            return dataNode.get("content").asText();
        }
        // Última alternativa: algunos esquemas ponen el texto directamente en el raíz.
        if (root.hasNonNull("text_chunk")) {
            return root.get("text_chunk").asText();
        }
        return null;
    }

    /**
     * Traduce una respuesta HTTP de error de Kibana a una excepción propia con
     * su {@link ErrorCode}. Nunca se registra la API Key.
     */
    private Mono<? extends Throwable> mapUpstreamError(ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(rawBody -> {
                    log.error("Kibana respondió con error {}. Cuerpo: {}", status.value(), rawBody);
                    return switch (status.value()) {
                        case 401 -> new AgentAuthenticationException(
                                ErrorCode.AUTH_INVALID, ErrorCode.AUTH_INVALID.defaultMessage());
                        case 403 -> new AgentAuthenticationException(
                                ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
                        case 404 -> new AgentUpstreamException(
                                ErrorCode.AGENT_NOT_FOUND, ErrorCode.AGENT_NOT_FOUND.defaultMessage());
                        default -> new AgentUpstreamException(
                                ErrorCode.UPSTREAM_ERROR,
                                "Kibana respondió con estado " + status.value() + ".");
                    };
                });
    }

    /** ¿La causa del error es un timeout (de conexión o de respuesta)? */
    private boolean isTimeout(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getSimpleName().toLowerCase().contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    private AgentUpstreamException toTimeoutException(Throwable t) {
        return new AgentUpstreamException(
                ErrorCode.TIMEOUT, ErrorCode.TIMEOUT.defaultMessage(), t);
    }
}

package com.bigfito.agentchat.web;

import com.bigfito.agentchat.exception.AgentBuilderException;
import com.bigfito.agentchat.exception.ErrorCode;
import com.bigfito.agentchat.model.PromptRequest;
import com.bigfito.agentchat.model.StreamChunk;
import com.bigfito.agentchat.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Expone el prompt del usuario como un stream SSE.
 *
 * <p>Se usa POST + {@code text/event-stream} (en lugar de {@code EventSource},
 * que solo admite GET) para no exponer el prompt en la URL ni en los logs de
 * acceso. La app actúa como proxy: reenvía al navegador los eventos que produce
 * el agente.</p>
 *
 * <p>Eventos SSE emitidos hacia el navegador:</p>
 * <ul>
 *   <li>{@code message}: un fragmento de texto (payload JSON {@code {"text": ...}}).</li>
 *   <li>{@code done}: la respuesta terminó correctamente.</li>
 *   <li>{@code error}: ocurrió un fallo durante el stream (payload JSON con
 *       {@code code} y {@code message}).</li>
 * </ul>
 *
 * <p>El texto de cada fragmento viaja como JSON (no como texto plano) porque el
 * formato SSE consume un espacio inicial tras {@code data:}; empaquetarlo en JSON
 * preserva de forma fiable espacios y saltos de línea entre palabras.</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatStreamController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /**
     * Recibe el prompt por POST y devuelve la respuesta del agente como stream SSE.
     *
     * @param request cuerpo JSON con el prompt (validado con {@code @Valid}).
     * @return flujo de eventos SSE ({@code message*}, luego {@code done}, o
     *         {@code error} si algo falla durante el stream).
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody PromptRequest request) {
        // Flux.defer captura también las excepciones síncronas de la validación
        // del servicio y las convierte en una señal de error del flujo.
        return Flux.defer(() -> chatService.ask(request.prompt()))
                .map(this::messageEvent)
                .concatWith(Mono.just(doneEvent()))
                .onErrorResume(this::toErrorEvent);
    }

    /** Evento SSE con un fragmento de texto, empaquetado como JSON. */
    private ServerSentEvent<String> messageEvent(StreamChunk chunk) {
        String payload = toJson(Map.of("text", chunk.text()));
        return ServerSentEvent.builder(payload).event("message").build();
    }

    /** Evento que indica al navegador que la respuesta terminó correctamente. */
    private ServerSentEvent<String> doneEvent() {
        return ServerSentEvent.<String>builder().event("done").data("").build();
    }

    /**
     * Convierte cualquier error del flujo en un evento SSE {@code error} con un
     * payload JSON entendible. Así el navegador puede mostrar el mensaje aunque
     * el fallo ocurra con el stream ya abierto.
     */
    private Flux<ServerSentEvent<String>> toErrorEvent(Throwable error) {
        ErrorCode code = (error instanceof AgentBuilderException abe)
                ? abe.getErrorCode()
                : ErrorCode.UPSTREAM_ERROR;
        log.error("Error en el stream de chat [{}]", code, error);

        String payload = toJson(Map.of("code", code.name(), "message", code.defaultMessage()));
        return Flux.just(ServerSentEvent.<String>builder(payload).event("error").build());
    }

    /** Serializa un payload a JSON; con un texto de respaldo si la serialización falla. */
    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("No se pudo serializar el payload a JSON", e);
            return "{\"message\":\"Ocurrió un error.\"}";
        }
    }
}

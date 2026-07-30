package com.bigfito.agentchat.service;

import com.bigfito.agentchat.client.AgentBuilderClient;
import com.bigfito.agentchat.exception.ErrorCode;
import com.bigfito.agentchat.exception.InvalidPromptException;
import com.bigfito.agentchat.model.StreamChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Orquesta la conversación con el agente: valida el prompt y delega en el
 * cliente. No contiene lógica HTTP (eso es responsabilidad del controlador y del
 * cliente), de acuerdo con el principio de responsabilidad única.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Longitud máxima aceptada para un prompt. */
    static final int MAX_PROMPT_LENGTH = 4_000;

    private final AgentBuilderClient client;

    public ChatService(AgentBuilderClient client) {
        this.client = client;
    }

    /**
     * Valida el prompt y devuelve la respuesta del agente como flujo de fragmentos.
     *
     * @param prompt texto escrito por el usuario.
     * @return flujo de {@link StreamChunk} con los deltas de texto del agente.
     * @throws InvalidPromptException si el prompt está vacío o es demasiado largo.
     */
    public Flux<StreamChunk> ask(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new InvalidPromptException(
                    ErrorCode.PROMPT_EMPTY, ErrorCode.PROMPT_EMPTY.defaultMessage());
        }
        String cleaned = prompt.trim();
        if (cleaned.length() > MAX_PROMPT_LENGTH) {
            throw new InvalidPromptException(
                    ErrorCode.PROMPT_TOO_LONG, ErrorCode.PROMPT_TOO_LONG.defaultMessage());
        }
        log.info("Nuevo prompt recibido ({} caracteres)", cleaned.length());
        return client.converseStream(cleaned);
    }
}

package com.bigfito.agentchat.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo JSON enviado a Kibana ({@code /api/agent_builder/converse/async}).
 *
 * <p>No se incluye {@code conversation_id}: cada interacción es independiente
 * (sin memoria conversacional). Si en el futuro se quiere memoria multi-turno,
 * basta con capturar el {@code conversation_id} que devuelve el stream y añadir
 * ese campo aquí para reenviarlo.</p>
 *
 * @param input   texto del prompt del usuario.
 * @param agentId identificador del agente que debe responder.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConverseRequest(
        String input,
        @JsonProperty("agent_id") String agentId) {
}

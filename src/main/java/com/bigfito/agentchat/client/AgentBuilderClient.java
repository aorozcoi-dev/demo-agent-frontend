package com.bigfito.agentchat.client;

import com.bigfito.agentchat.model.StreamChunk;
import reactor.core.publisher.Flux;

/**
 * Puerta de enlace (<em>gateway</em>) hacia el API de conversación de
 * Elastic Agent Builder (Kibana).
 *
 * <p>El servicio depende de esta abstracción, no de la implementación concreta
 * con {@code WebClient}, lo que hace triviales las pruebas (inversión de
 * dependencias).</p>
 */
public interface AgentBuilderClient {

    /**
     * Envía un prompt al agente y devuelve la respuesta como flujo de fragmentos.
     * Cada elemento es un trozo de texto listo para pintarse en pantalla.
     *
     * @param prompt texto del usuario (ya validado y saneado por el servicio).
     * @return flujo de {@link StreamChunk} con los deltas de texto del agente.
     */
    Flux<StreamChunk> converseStream(String prompt);
}

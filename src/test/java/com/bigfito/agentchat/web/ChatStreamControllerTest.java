package com.bigfito.agentchat.web;

import com.bigfito.agentchat.client.AgentBuilderClient;
import com.bigfito.agentchat.exception.AgentUpstreamException;
import com.bigfito.agentchat.exception.ErrorCode;
import com.bigfito.agentchat.model.PromptRequest;
import com.bigfito.agentchat.model.StreamChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "elastic.agent.kibana-base-url=http://localhost:59999",
        "elastic.agent.api-key=test-key",
        "elastic.agent.agent-id=test-agent"
})
@DisplayName("ChatStreamController: secuencia de eventos SSE")
class ChatStreamControllerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private AgentBuilderClient agentBuilderClient;

    private WebTestClient webTestClient;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /** Extrae el campo {@code text} del payload JSON de un evento "message". */
    private String textoDe(String data) {
        return jsonMapper.readTree(data).get("text").asText();
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("emite eventos 'message' por cada fragmento y termina con 'done'")
    void streamsMessagesThenDone() {
        when(agentBuilderClient.converseStream(any()))
                .thenReturn(Flux.just(new StreamChunk("Hola"), new StreamChunk(" mundo")));

        FluxExchangeResult<ServerSentEvent<String>> result = webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new PromptRequest("hola"))
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() { });

        StepVerifier.create(result.getResponseBody())
                .expectNextMatches(e -> "message".equals(e.event()) && "Hola".equals(textoDe(e.data())))
                // El espacio inicial de " mundo" debe preservarse gracias al empaquetado JSON.
                .expectNextMatches(e -> "message".equals(e.event()) && " mundo".equals(textoDe(e.data())))
                .expectNextMatches(e -> "done".equals(e.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("un fallo durante el stream se convierte en un evento 'error'")
    void streamErrorBecomesErrorEvent() {
        when(agentBuilderClient.converseStream(any()))
                .thenReturn(Flux.error(new AgentUpstreamException(
                        ErrorCode.UPSTREAM_ERROR, "fallo simulado")));

        FluxExchangeResult<ServerSentEvent<String>> result = webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new PromptRequest("hola"))
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() { });

        StepVerifier.create(result.getResponseBody())
                .expectNextMatches(e -> "error".equals(e.event())
                        && e.data() != null
                        && e.data().contains(ErrorCode.UPSTREAM_ERROR.name()))
                .verifyComplete();
    }

    @Test
    @DisplayName("prompt vacío responde 400 antes de abrir el stream")
    void emptyPromptReturnsBadRequest() {
        webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new PromptRequest("   "))
                .exchange()
                .expectStatus().isBadRequest();
    }
}

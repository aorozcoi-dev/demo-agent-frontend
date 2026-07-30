package com.bigfito.agentchat.client;

import com.bigfito.agentchat.config.ElasticAgentProperties;
import com.bigfito.agentchat.exception.AgentAuthenticationException;
import com.bigfito.agentchat.exception.AgentUpstreamException;
import com.bigfito.agentchat.exception.ErrorCode;
import com.bigfito.agentchat.model.StreamChunk;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentBuilderWebClient: parseo del stream SSE y mapeo de errores")
class AgentBuilderWebClientTest {

    private MockWebServer server;
    private AgentBuilderWebClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        ElasticAgentProperties props = new ElasticAgentProperties(
                server.url("/").toString(),
                "clave-de-prueba",
                "elastic-ai-agent",
                "",                       // space por defecto
                Duration.ofSeconds(2),
                Duration.ofSeconds(10));

        WebClient webClient = WebClient.builder()
                .baseUrl(props.kibanaBaseUrl())
                .build();

        client = new AgentBuilderWebClient(webClient, props, JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("emite un StreamChunk por cada evento message_chunk e ignora el resto")
    void emitsChunksForMessageEvents() throws InterruptedException {
        // Formato real de Kibana 9.4.x: el tipo va en la línea "event:" y el JSON
        // anida el contenido bajo "data" (sin campo "type" en el cuerpo).
        String sse = """
                event: conversation_id_set
                data: {"data":{"conversation_id":"abc-123"}}

                event: reasoning
                data: {"data":{"transient":true,"reasoning":"Analyzing the request"}}

                event: message_chunk
                data: {"data":{"message_id":"1","text_chunk":"Hola"}}

                event: message_chunk
                data: {"data":{"message_id":"1","text_chunk":" mundo"}}

                event: round_complete
                data: {"data":{}}

                """;

        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sse));

        StepVerifier.create(client.converseStream("hola"))
                .expectNext(new StreamChunk("Hola"))
                .expectNext(new StreamChunk(" mundo"))
                .verifyComplete();

        // Verifica que se llamó al endpoint correcto con las cabeceras exigidas.
        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/agent_builder/converse/async");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("ApiKey clave-de-prueba");
        assertThat(recorded.getHeader("kbn-xsrf")).isEqualTo("true");
    }

    @Test
    @DisplayName("401 se mapea a AgentAuthenticationException con AUTH_INVALID")
    void unauthorizedMapsToAuthException() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"));

        StepVerifier.create(client.converseStream("hola"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AgentAuthenticationException.class)
                        .extracting("errorCode")
                        .isEqualTo(ErrorCode.AUTH_INVALID))
                .verify();
    }

    @Test
    @DisplayName("403 se mapea a AgentAuthenticationException con FORBIDDEN")
    void forbiddenMapsToAuthException() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody(""));

        StepVerifier.create(client.converseStream("hola"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AgentAuthenticationException.class)
                        .extracting("errorCode")
                        .isEqualTo(ErrorCode.FORBIDDEN))
                .verify();
    }

    @Test
    @DisplayName("500 se mapea a AgentUpstreamException con UPSTREAM_ERROR")
    void serverErrorMapsToUpstreamException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        StepVerifier.create(client.converseStream("hola"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AgentUpstreamException.class)
                        .extracting("errorCode")
                        .isEqualTo(ErrorCode.UPSTREAM_ERROR))
                .verify();
    }
}

package com.bigfito.agentchat.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Configura el {@link WebClient} usado para hablar con Kibana (Agent Builder).
 *
 * <p>Se apunta la {@code baseUrl} a Kibana y se ajustan los timeouts de conexión
 * y de respuesta a partir de {@link ElasticAgentProperties}. Las cabeceras de
 * autenticación se añaden por petición en el cliente, no aquí, para no filtrar
 * la API Key a otros usos del {@code WebClient}.</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * {@code WebClient} preconfigurado hacia Kibana.
     *
     * @param props configuración de Elastic Agent Builder.
     * @return cliente HTTP reactivo listo para consumir el endpoint de streaming.
     */
    @Bean
    public WebClient kibanaWebClient(ElasticAgentProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) props.connectTimeout().toMillis())
                .responseTimeout(props.responseTimeout());

        return WebClient.builder()
                .baseUrl(props.kibanaBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

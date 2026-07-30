package com.bigfito.agentchat;

import com.bigfito.agentchat.config.ElasticAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Punto de entrada de la aplicación.
 *
 * <p>App web que actúa como <em>proxy de streaming</em> entre el navegador y
 * Elastic Agent Builder (a través de Kibana): recibe un <em>prompt</em>, se lo
 * envía al agente y reenvía la respuesta al navegador token a token (SSE).</p>
 *
 * <p>La API Key vive únicamente en el backend; nunca llega al navegador.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ElasticAgentProperties.class)
public class AgentChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentChatApplication.class, args);
    }
}
